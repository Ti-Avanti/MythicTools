package gg.fotia.mythictools.summoning;

import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.boss.BossManager;
import gg.fotia.mythictools.boss.BossRepository;
import gg.fotia.mythictools.runtime.BukkitTaskScheduler;
import gg.fotia.mythictools.runtime.OwnedTasks;
import gg.fotia.mythictools.summoning.config.*;
import gg.fotia.mythictools.summoning.effect.ParticleEffects;
import gg.fotia.mythictools.summoning.visual.*;
import gg.fotia.mythictools.text.MessageRenderer;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 单一调度器驱动仪式；预览共用表现逻辑，但无法进入真实生成与消耗分支。 */
public final class SummonService implements AutoCloseable {
    private record Cooldown(UUID player, String item) { }
    private final MythicToolsPlugin plugin;
    private final BossManager bosses;
    private final BossRepository bossConfigs;
    private final SummonRepository repository;
    private final SummonSettings settings;
    private final SummonItems items;
    private final SummonRefunds refunds;
    private final MessageRenderer messages;
    private final VisualProviders visuals;
    private final ParticleEffects effects;
    private final SummonFeedback feedback;
    private final OwnedTasks tasks;
    private final Map<UUID, SummonRitual> rituals = new LinkedHashMap<>();
    private final Map<UUID, SummonRitual> pending = new HashMap<>();
    private final Map<UUID, SummonRitual> previews = new HashMap<>();
    private final Map<Cooldown, Long> cooldowns = new HashMap<>();

    public SummonService(MythicToolsPlugin plugin, BossManager bosses, BossRepository bossConfigs,
                         SummonRepository repository, SummonSettings settings, SummonItems items,
                         SummonRefunds refunds, MessageRenderer messages, VisualProviders visuals) {
        this(plugin, bosses, bossConfigs, repository, settings, items, refunds, messages, visuals,
                new OwnedTasks(new BukkitTaskScheduler(plugin)));
    }

    SummonService(MythicToolsPlugin plugin, BossManager bosses, BossRepository bossConfigs,
                  SummonRepository repository, SummonSettings settings, SummonItems items,
                  SummonRefunds refunds, MessageRenderer messages, VisualProviders visuals, OwnedTasks tasks) {
        this.plugin = plugin; this.bosses = bosses; this.bossConfigs = bossConfigs;
        this.repository = repository; this.settings = settings; this.items = items;
        this.refunds = refunds; this.messages = messages; this.visuals = visuals;
        this.effects = new ParticleEffects(settings);
        this.feedback = new SummonFeedback(messages, effects);
        this.tasks = tasks;
    }
    public void start() { tasks.repeating(this::tick, 1, settings.periodTicks()); }
    public int pendingCount() { return pending.size(); }
    public int sessionCount() { return rituals.size(); }
    public SummonRepository repository() { return repository; }
    public SummonItems items() { return items; }
    public int maxLayers() { return settings.maxLayers(); }
    public void give(Player player, SummonDefinition definition, int amount) {
        int maximum = definition.item().getMaxStackSize();
        for (int remaining = amount; remaining > 0; remaining -= maximum) {
            refunds.give(player.getUniqueId(), items.create(definition, Math.min(maximum, remaining)));
        }
    }
    public VisualProviders visuals() { return visuals; }
    public boolean previewing(UUID player) { return previews.containsKey(player); }

    public void validate(SummonDefinition definition) {
        if (bossConfigs.boss(definition.bossId()) == null) throw new IllegalArgumentException("summon.error.boss");
        visuals.validate(definition.visual());
    }
    public boolean tryUse(Player player) {
        String id = items.identify(player.getInventory().getItemInMainHand());
        if (id == null) return false;
        try {
            SummonDefinition definition = repository.get(id);
            if (definition == null || !definition.enabled()) throw new IllegalArgumentException("summon.error.disabled");
            if (pending.containsKey(player.getUniqueId()) || previewing(player.getUniqueId())) throw new IllegalArgumentException("summon.error.busy");
            checkPlayer(player, definition);
            validate(definition);
            if (!plugin.settings().overrideBoss()) throw new IllegalArgumentException("summon.error.boss-disabled");
            if (cooldowns.getOrDefault(new Cooldown(player.getUniqueId(), id), 0L) > System.currentTimeMillis()) {
                throw new IllegalArgumentException("summon.error.cooldown");
            }
            if (rituals.size() >= settings.maxSessions() || occupied(definition.bossId()) >= definition.maxActive()) {
                throw new IllegalArgumentException("summon.error.limit");
            }
            var origin = SummonLocation.find(player, definition);
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getAmount() < definition.consume()) throw new IllegalArgumentException("summon.error.amount");
            ItemStack escrow = definition.consume() == 0 ? null : held.clone();
            if (escrow != null) {
                escrow.setAmount(definition.consume());
                held.setAmount(held.getAmount() - definition.consume());
                player.getInventory().setItemInMainHand(held.getAmount() == 0 ? null : held);
            }
            SummonRitual ritual = new SummonRitual(player.getUniqueId(), definition, origin, player.getLocation(), escrow, false, null);
            rituals.put(ritual.id, ritual);
            pending.put(ritual.player, ritual);
            try { begin(ritual); } catch (RuntimeException | LinkageError error) { cancel(ritual, false); throw error; }
        } catch (RuntimeException | LinkageError error) { report(player, error); }
        return true;
    }

    public void preview(Player player, SummonDefinition definition, Runnable returnToEditor) {
        stopPreview(player.getUniqueId(), false);
        if (pending.containsKey(player.getUniqueId()) || rituals.size() >= settings.maxSessions()) throw new IllegalArgumentException("summon.error.busy");
        visuals.validate(definition.visual());
        var origin = SummonLocation.find(player, definition);
        SummonRitual ritual = new SummonRitual(player.getUniqueId(), definition, origin, player.getLocation(), null, true, returnToEditor);
        rituals.put(ritual.id, ritual); previews.put(ritual.player, ritual);
        try { begin(ritual); } catch (RuntimeException | LinkageError error) { cancel(ritual, false); throw error; }
        messages.send(player, "summon.preview", Map.of());
    }
    public void stopPreview(UUID player, boolean returnToEditor) {
        SummonRitual ritual = previews.get(player);
        if (ritual != null) cancel(ritual, returnToEditor);
    }
    public void interrupt(UUID player, boolean damage) {
        SummonRitual ritual = pending.get(player);
        if (ritual != null && (!damage || ritual.definition.damageInterrupt())) cancel(ritual, false);
        if (!damage) stopPreview(player, false);
    }

    private void begin(SummonRitual ritual) {
        if (ritual.definition.visual().duringWindup()) createVisual(ritual);
        feedback.sound(ritual, "start");
        if (!ritual.preview) feedback.message(Bukkit.getPlayer(ritual.player), ritual, "start");
    }
    private void createVisual(SummonRitual ritual) {
        if (ritual.visualStarted) return;
        ritual.visual = visuals.create(ritual.definition.visual(), ritual.origin, ritual.preview ? ritual.player : null, settings.range());
        ritual.visualStarted = true;
        ritual.visual.animate(ritual.definition.visual().startAnimation(), false);
    }
    private int occupied(String boss) {
        return bosses.aliveCount(boss) + (int) pending.values().stream().filter(r -> r.definition.bossId().equals(boss)).count();
    }
    private void checkPlayer(Player player, SummonDefinition definition) {
        if (!player.isOnline() || player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) throw new IllegalArgumentException("summon.error.player");
        if (!definition.permission().isBlank() && !player.hasPermission(definition.permission())) throw new IllegalArgumentException("common.no-permission");
        if (!definition.worlds().isEmpty() && !definition.worlds().contains(player.getWorld().getName())) throw new IllegalArgumentException("summon.error.world");
    }
    private void tick() {
        effects.beginFrame();
        cooldowns.values().removeIf(until -> until <= System.currentTimeMillis());
        for (SummonRitual ritual : List.copyOf(rituals.values())) {
            try { tick(ritual); } catch (RuntimeException | LinkageError error) {
                cancel(ritual, false);
                Player player = Bukkit.getPlayer(ritual.player);
                if (player != null) report(player, error);
                plugin.getLogger().log(java.util.logging.Level.WARNING, "召唤仪式失败: " + ritual.definition.id(), error);
            }
        }
    }
    private void tick(SummonRitual ritual) {
        var config = ritual.definition;
        var visual = config.visual();
        Player player = Bukkit.getPlayer(ritual.player);
        if (!ritual.origin.getWorld().isChunkLoaded(ritual.origin.getBlockX() >> 4, ritual.origin.getBlockZ() >> 4)
                || (!visual.type().equals("none") && visuals.get(visual.type()) == null)) { cancel(ritual, false); return; }
        if (ritual.pending() || ritual.preview) {
            if (player == null || !player.isOnline() || player.isDead() || !player.getWorld().equals(ritual.castingOrigin.getWorld())
                    || (!ritual.preview && player.getLocation().distanceSquared(ritual.castingOrigin) > config.moveRadius() * config.moveRadius())) {
                cancel(ritual, false); return;
            }
        }
        ritual.visual.update(ritual.age, effects.viewers(ritual.origin, ritual.preview ? ritual.player : null));
        if (ritual.pending()) {
            if (ritual.age >= config.windupTicks()) { finishWindup(ritual, player); }
            else {
                if (ritual.age >= config.windupTicks() * config.chargeStart() && !ritual.chargeStarted) {
                    ritual.chargeStarted = true; ritual.visual.animate(visual.chargeAnimation(), true); feedback.sound(ritual, "charge");
                }
                feedback.countdown(player, ritual);
                effects.render(config.layers(), ritual.origin, ritual.preview ? ritual.player : null, ritual.age, config.windupTicks(), false);
            }
        } else {
            int since = ritual.age - ritual.successAge;
            int release = config.layers().stream().mapToInt(layer -> layer.releaseTicks()).max().orElse(0);
            effects.render(config.layers(), ritual.origin, ritual.preview ? ritual.player : null, since, config.windupTicks(), true);
            if (since >= visual.releaseTicks() && ritual.endingAge < 0 && !ritual.idleStarted) {
                ritual.idleStarted = true; ritual.visual.animate(visual.idleAnimation(), true);
            }
            boolean ended = visual.removeOnBossEnd() && ritual.fight != null && !bosses.isFightActive(ritual.fight);
            int lifetime = visual.type().equals("none") ? 0 : visual.durationTicks();
            int endingTicks = visual.endAnimation().isBlank() ? 0 : visual.endTicks();
            if ((since >= lifetime || ended) && ritual.endingAge < 0) {
                ritual.endingAge = ritual.age; ritual.visual.animate(visual.endAnimation(), false);
            }
            if (ritual.endingAge >= 0 && ritual.age - ritual.endingAge >= endingTicks) {
                ritual.visual.close(); ritual.visual = VisualHandle.NONE;
                if (since >= release) { remove(ritual, ritual.preview); return; }
            }
        }
        ritual.age += settings.periodTicks();
    }
    private void finishWindup(SummonRitual ritual, Player player) {
        if (!ritual.preview) {
            checkPlayer(player, ritual.definition);
            validate(ritual.definition);
            SummonLocation.validate(ritual.origin);
            if (occupied(ritual.definition.bossId()) > ritual.definition.maxActive()) throw new IllegalArgumentException("summon.error.limit");
        }
        createVisual(ritual);
        if (!ritual.preview) {
            var spawned = bosses.summon(ritual.definition.bossId(), ritual.origin)
                    .orElseThrow(() -> new IllegalArgumentException("summon.error.spawn"));
            ritual.fight = spawned.fightId();
            ritual.escrow = null;
            pending.remove(ritual.player, ritual);
            cooldowns.put(new Cooldown(ritual.player, ritual.definition.id()), System.currentTimeMillis() + ritual.definition.cooldownMillis());
        }
        ritual.successAge = ritual.age;
        feedback.clear(player, ritual);
        ritual.visual.animate(ritual.definition.visual().releaseAnimation(), false);
        feedback.sound(ritual, "success");
        if (!ritual.preview) feedback.message(player, ritual, "success");
    }
    private void cancel(SummonRitual ritual, boolean returnToEditor) {
        if (!rituals.containsKey(ritual.id)) return;
        ItemStack refund = ritual.escrow; ritual.escrow = null;
        try {
            if (refund != null) refunds.give(ritual.player, refund);
            Player player = Bukkit.getPlayer(ritual.player);
            if (ritual.pending()) {
                feedback.clear(player, ritual); feedback.sound(ritual, "cancel");
                if (player != null && !ritual.preview) feedback.message(player, ritual, "cancelled");
            }
        } finally {
            remove(ritual, returnToEditor);
        }
    }
    private void remove(SummonRitual ritual, boolean returnToEditor) {
        rituals.remove(ritual.id); pending.remove(ritual.player, ritual); previews.remove(ritual.player, ritual);
        try { ritual.visual.close(); } finally {
            if (returnToEditor && ritual.returnToEditor != null) tasks.execute(ritual.returnToEditor);
        }
    }
    public void report(Player player, Throwable error) {
        String key = String.valueOf(error.getMessage());
        if (messages.containsText(player, key)) messages.send(player, key, Map.of());
        else messages.send(player, "common.config-error", Map.of("reason", key));
    }
    public void close() {
        for (SummonRitual ritual : List.copyOf(rituals.values())) {
            try { cancel(ritual, false); } catch (RuntimeException | LinkageError error) { plugin.getLogger().warning(error.toString()); }
        }
        tasks.close();
    }
}
