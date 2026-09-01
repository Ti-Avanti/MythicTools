package gg.fotia.mythictools.boss;

import gg.fotia.mythictools.integration.MythicMobGateway;
import gg.fotia.mythictools.lang.LocaleService;
import gg.fotia.mythictools.reward.RewardGrant;
import gg.fotia.mythictools.reward.FirstDefeatRewardService;
import gg.fotia.mythictools.reward.FirstDefeatSource;
import gg.fotia.mythictools.reward.RewardRecipient;
import gg.fotia.mythictools.reward.RewardService;
import gg.fotia.mythictools.reward.WeightedRewardSelector;
import gg.fotia.mythictools.runtime.BukkitTaskScheduler;
import gg.fotia.mythictools.runtime.OwnedTasks;
import gg.fotia.mythictools.spawning.BiomeSpawnRule;
import gg.fotia.mythictools.spawning.SpawnLocationFinder;
import gg.fotia.mythictools.text.MessageRenderer;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

/** Boss 生成、跨阶段伤害累计、最终结算与排名消息模块。 */
public final class BossManager implements Listener {
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("0.##");
    private final JavaPlugin plugin;
    private final BossConfigView repository;
    private final MythicMobGateway mythicMobs;
    private final SpawnLocationFinder locationFinder;
    private final WeightedRewardSelector selector;
    private final RewardService rewards;
    private final FirstDefeatRewardService firstDefeatRewards;
    private final LocaleService locales;
    private final MessageRenderer messages;
    private final boolean enabled;
    private final BossFightRegistry fights = new BossFightRegistry();
    private final Map<String, Long> nextPointSpawns = new HashMap<>();
    private final Map<AttemptKey, Long> biomeAttempts = new HashMap<>();
    private final Set<UUID> settlingEntities = new HashSet<>();
    private final Map<UUID, BossDropAction> settlingDropActions = new HashMap<>();
    private final OwnedTasks tasks;

    public BossManager(
            JavaPlugin plugin,
            BossConfigView repository,
            MythicMobGateway mythicMobs,
            SpawnLocationFinder locationFinder,
            WeightedRewardSelector selector,
            RewardService rewards,
            FirstDefeatRewardService firstDefeatRewards,
            LocaleService locales,
            MessageRenderer messages,
            boolean enabled) {
        this(plugin, repository, mythicMobs, locationFinder, selector, rewards, firstDefeatRewards,
                locales, messages, enabled,
                new OwnedTasks(new BukkitTaskScheduler(plugin)));
    }

    public BossManager(
            JavaPlugin plugin,
            BossConfigView repository,
            MythicMobGateway mythicMobs,
            SpawnLocationFinder locationFinder,
            WeightedRewardSelector selector,
            RewardService rewards,
            FirstDefeatRewardService firstDefeatRewards,
            LocaleService locales,
            MessageRenderer messages,
            boolean enabled,
            OwnedTasks tasks) {
        this.plugin = plugin;
        this.repository = repository;
        this.mythicMobs = mythicMobs;
        this.locationFinder = locationFinder;
        this.selector = selector;
        this.rewards = rewards;
        this.firstDefeatRewards = firstDefeatRewards;
        this.locales = locales;
        this.messages = messages;
        this.enabled = enabled;
        this.tasks = tasks;
    }

    /** 启动 Boss 生成检查。 */
    public void start() {
        stop(false);
        if (!enabled) {
            return;
        }
        tasks.beginGeneration();
        long now = System.currentTimeMillis();
        for (BossConfig boss : repository.bosses()) {
            for (BossSpawner spawner : boss.spawners()) {
                if (spawner.type() == BossSpawnType.POINT) {
                    nextPointSpawns.put(key(boss, spawner),
                            spawner.pointSchedule().initialNext(Instant.ofEpochMilli(now)).toEpochMilli());
                }
            }
        }
        tasks.repeating(this::tickSpawners, 100L, 100L);
    }

    /** 停止任务；插件关闭时同时移除未完成 Boss。 */
    public void stop(boolean removeBosses) {
        tasks.cancelAll();
        List<UUID> entityIds = fights.entityIds();
        fights.clear();
        settlingEntities.clear();
        settlingDropActions.clear();
        biomeAttempts.clear();
        nextPointSpawns.clear();
        if (removeBosses) {
            for (UUID entityId : entityIds) {
                mythicMobs.activeMob(entityId).ifPresent(ActiveMob::remove);
            }
        }
    }

    /** 判断实体是否属于仍在结算或切换阶段的 Boss 战。 */
    public boolean isTracked(UUID entityId) {
        return fights.containsEntity(entityId) || settlingEntities.contains(entityId);
    }

    /** 返回该 Boss 死亡事件应执行的掉落策略；空值表示并非 MythicTools 管理的 Boss。 */
    public Optional<BossDropAction> dropAction(UUID entityId) {
        BossFight fight = fights.entity(entityId);
        if (fight != null) {
            return Optional.of(fight.config.lootPolicy().dropAction(fight.isFinalStage()));
        }
        return Optional.ofNullable(settlingDropActions.get(entityId));
    }

    /** 返回指定 Boss 当前存活场数。 */
    public int aliveCount(String bossId) {
        return fights.bossCount(bossId);
    }

    /** 返回当前仍由本管理器持有的 Boss 战斗数。 */
    public int activeFightCount() {
        return fights.size();
    }

    /** 返回指定 Boss 第一场战斗的当前阶段，从 1 开始。 */
    public int currentPhase(String bossId) {
        return firstFight(bossId).map(fight -> fight.phaseIndex + 1).orElse(0);
    }

    /** 返回指定 Boss 总阶段数。 */
    public int totalPhases(String bossId) {
        BossConfig config = repository.boss(bossId);
        return config == null ? 0 : config.totalPhases();
    }

    /** 返回原生阶段 Boss 当前由 MythicMobs 管理的 stance；未激活或无 stance 时为空。 */
    public String currentNativeStance(String bossId) {
        return firstFight(bossId)
                .filter(fight -> fight.config.phaseMode() == BossPhaseMode.MYTHIC_NATIVE)
                .flatMap(fight -> mythicMobs.activeMob(fight.entityId))
                .map(ActiveMob::getStance)
                .filter(stance -> stance != null && !stance.isBlank())
                .orElse("");
    }

    /** 返回指定 Boss 第一场战斗的生命值。 */
    public double currentHealth(String bossId) {
        return firstLivingEntity(bossId).map(LivingEntity::getHealth).orElse(0.0);
    }

    /** 返回指定 Boss 第一场战斗的最大生命值。 */
    public double maximumHealth(String bossId) {
        return firstLivingEntity(bossId).map(LivingEntity::getMaxHealth).orElse(0.0);
    }

    /** 返回伤害第一玩家及数值。 */
    public Optional<Map.Entry<String, Double>> topDamage(String bossId) {
        return firstFight(bossId).flatMap(fight -> fight.damage.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> Map.entry(fight.playerNames.getOrDefault(entry.getKey(), entry.getKey().toString()),
                        entry.getValue())));
    }

    /** 返回固定 Boss 点的下次生成秒数。 */
    public long secondsUntilNext(String bossId, String spawnerId) {
        Long next = nextPointSpawns.get(bossId + ":" + spawnerId);
        return next == null ? -1L : Math.max(0L, (next - System.currentTimeMillis() + 999L) / 1000L);
    }

    /** 在管理员位置立即生成指定 Boss。 */
    public boolean spawnNow(String bossId, Location location) {
        if (!enabled) {
            return false;
        }
        BossConfig boss = repository.boss(bossId);
        if (boss == null) {
            return false;
        }
        BossSpawner manual = new BossSpawner("manual", BossSpawnType.POINT, true,
                1L, 1.0, Integer.MAX_VALUE, 0, BossPointSchedule.fixedInterval(1L),
                Set.of(), Set.of(), 0, 0, location);
        int before = aliveCount(bossId);
        spawnFight(boss, manual, location);
        return aliveCount(bossId) > before;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        BossFight fight = fights.entity(event.getEntity().getUniqueId());
        if (fight == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player player = resolvePlayer(event.getDamager());
        if (player == null) {
            return;
        }
        double credited = Math.min(event.getFinalDamage(), target.getHealth());
        if (credited > 0.0) {
            fight.record(player.getUniqueId(), player.getName(), credited);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBossDeath(MythicMobDeathEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        BossFight fight = fights.detachEntity(entityId);
        if (fight == null) {
            return;
        }
        BossDropAction dropAction = fight.config.lootPolicy().dropAction(fight.isFinalStage());
        settlingEntities.add(entityId);
        settlingDropActions.put(entityId, dropAction);
        Location deathLocation = event.getEntity().getLocation();
        Player killer = event.getKiller() instanceof Player player ? player : null;
        if (fight.config.phaseMode().replacesEntityOnStageDeath() && !fight.isFinalStage()) {
            fight.phaseIndex++;
            tasks.execute(() -> transition(fight, deathLocation));
        } else {
            finish(fight, deathLocation, killer);
        }
        tasks.execute(() -> {
            settlingEntities.remove(entityId);
            settlingDropActions.remove(entityId);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        biomeAttempts.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    private void tickSpawners() {
        sweepInactiveFights();
        long now = System.currentTimeMillis();
        for (BossConfig boss : repository.bosses()) {
            for (BossSpawner spawner : boss.spawners()) {
                if (!spawner.enabled()) {
                    continue;
                }
                if (spawner.type() == BossSpawnType.POINT) {
                    tickPoint(boss, spawner, now);
                } else if (activeAtSpawner(key(boss, spawner)) < spawner.maxActive()
                        && hasEnoughOnlinePlayers(spawner)) {
                    tickBiome(boss, spawner, now);
                }
            }
        }
    }

    private void tickPoint(BossConfig boss, BossSpawner spawner, long now) {
        String key = key(boss, spawner);
        if (now < nextPointSpawns.getOrDefault(key, now)) {
            return;
        }
        nextPointSpawns.put(key,
                spawner.pointSchedule().nextAfter(Instant.ofEpochMilli(now)).toEpochMilli());
        if (activeAtSpawner(key) >= spawner.maxActive() || !hasEnoughOnlinePlayers(spawner)) {
            return;
        }
        Location location = spawner.location();
        if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            spawnFight(boss, spawner, location);
        }
    }

    private void tickBiome(BossConfig boss, BossSpawner spawner, long now) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if ((!spawner.worlds().isEmpty() && !spawner.worlds().contains(player.getWorld().getName()))
                    || !spawner.biomes().contains(player.getLocation().getBlock().getBiome())) {
                continue;
            }
            AttemptKey key = new AttemptKey(player.getUniqueId(), boss.id(), spawner.id());
            long last = biomeAttempts.getOrDefault(key, 0L);
            if (now - last < Duration.ofSeconds(spawner.intervalSeconds()).toMillis()) {
                continue;
            }
            biomeAttempts.put(key, now);
            if (ThreadLocalRandom.current().nextDouble() > spawner.chance()) {
                continue;
            }
            BossPhase phase = boss.initialPhase();
            BiomeSpawnRule locatorRule = new BiomeSpawnRule(
                    key.toString(), phase.mobId(), null, spawner.worlds(), spawner.biomes(), 1.0,
                    spawner.intervalSeconds(), spawner.minDistance(), spawner.maxDistance(),
                    player.getWorld().getMinHeight(), player.getWorld().getMaxHeight(),
                    0, 15, 1, 1, 1, 1, spawner.maxDistance(), phase.level(), 0L, true);
            locationFinder.find(player, locatorRule).ifPresent(location -> spawnFight(boss, spawner, location));
            if (activeAtSpawner(boss.id() + ":" + spawner.id()) >= spawner.maxActive()) {
                return;
            }
        }
    }

    private void spawnFight(BossConfig boss, BossSpawner spawner, Location location) {
        BossPhase first = boss.initialPhase();
        mythicMobs.spawn(first.mobId(), location, first.level()).ifPresent(activeMob -> {
            activeMob.getEntity().setSaveToDisk(false);
            BossFight fight = new BossFight(boss, key(boss, spawner), activeMob.getUniqueId());
            fights.add(fight);
            broadcast(boss, boss.spawnBroadcast(), location, null);
        });
    }

    private void transition(BossFight fight, Location location) {
        BossPhase next = fight.config.phases().get(fight.phaseIndex);
        mythicMobs.spawn(next.mobId(), location, next.level()).ifPresentOrElse(activeMob -> {
            activeMob.getEntity().setSaveToDisk(false);
            fight.entityId = activeMob.getUniqueId();
            fights.bindEntity(fight);
        }, () -> {
            plugin.getLogger().severe("Boss " + fight.config.id() + " 无法生成阶段 " + (fight.phaseIndex + 1));
            removeFight(fight);
        });
    }

    private void finish(BossFight fight, Location location, Player killer) {
        removeFight(fight);
        Map<UUID, List<RewardGrant>> awarded = new LinkedHashMap<>();
        List<Map.Entry<UUID, Double>> ranking = fight.damage.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(fight.config.rewards().maxRecipients())
                .toList();
        if (fight.config.lootPolicy().awardsMythicToolsRewardsOnFinalDeath()) {
            if (fight.config.rewards().rankingEnabled()) {
                for (int index = 0; index < ranking.size(); index++) {
                    List<RewardGrant> grants = grantsFor(
                            fight.config.rewards().rankRewards().getOrDefault(index + 1, List.of()));
                    awarded.computeIfAbsent(ranking.get(index).getKey(), ignored -> new ArrayList<>()).addAll(grants);
                }
            }
            if (fight.config.rewards().killerEnabled() && killer != null) {
                awarded.computeIfAbsent(killer.getUniqueId(), ignored -> new ArrayList<>())
                        .addAll(grantsFor(fight.config.rewards().killerRewards()));
                fight.playerNames.put(killer.getUniqueId(), killer.getName());
            }
            for (Map.Entry<UUID, List<RewardGrant>> entry : awarded.entrySet()) {
                UUID playerId = entry.getKey();
                Player online = Bukkit.getPlayer(playerId);
                OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
                String name = fight.playerNames.getOrDefault(playerId,
                        offline.getName() == null ? playerId.toString() : offline.getName());
                rewards.deliver(playerId, name, online, entry.getValue(), location,
                        Map.of("boss", fight.config.id(), "player", name, "location", formatLocation(location)), true);
            }
            awardFirstDefeat(fight, location, killer);
        }
        broadcast(fight.config, fight.config.deathBroadcast(), location, killer);
        if (fight.config.lootPolicy().awardsMythicToolsRewardsOnFinalDeath()
                && fight.config.rewards().rankingChatEnabled()) {
            showRanking(fight, ranking, awarded);
        }
    }

    private void awardFirstDefeat(BossFight fight, Location location, Player killer) {
        var config = fight.config.rewards().firstDefeatRewards();
        if (!config.enabled() || killer == null) {
            return;
        }
        List<RewardRecipient> recipients = BossFirstDefeatRecipients.select(
                        config.scope(), config.recipient(), fight.damage, killer.getUniqueId()).stream()
                .map(playerId -> {
                    Player online = Bukkit.getPlayer(playerId);
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
                    String name = fight.playerNames.getOrDefault(playerId,
                            offline.getName() == null ? playerId.toString() : offline.getName());
                    return new RewardRecipient(playerId, name, online);
                }).toList();
        firstDefeatRewards.award(
                FirstDefeatSource.boss(fight.config.id()), config, recipients, location,
                Map.of("boss", fight.config.id(), "location", formatLocation(location)), true)
                .exceptionally(failure -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "Boss 首次击败奖励发放失败: " + fight.config.id(), failure);
                    return null;
                });
    }

    private List<RewardGrant> grantsFor(List<GroupReward> groups) {
        List<RewardGrant> result = new ArrayList<>();
        for (GroupReward group : groups) {
            result.addAll(selector.selectGroup(group.groupId(), group.copies()));
        }
        return result;
    }

    private void showRanking(
            BossFight fight,
            List<Map.Entry<UUID, Double>> ranking,
            Map<UUID, List<RewardGrant>> awarded) {
        Map<String, List<Player>> viewersByLocale = new LinkedHashMap<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewersByLocale.computeIfAbsent(locales.locale(viewer), ignored -> new ArrayList<>()).add(viewer);
        }
        for (Map.Entry<String, List<Player>> group : viewersByLocale.entrySet()) {
            String locale = group.getKey();
            List<Component> shared = rankingTextsUsePapi(locale)
                    ? null : buildRankingLines(null, locale, fight, ranking, awarded);
            for (Player viewer : group.getValue()) {
                List<Component> lines = shared != null
                        ? shared : buildRankingLines(viewer, locale, fight, ranking, awarded);
                lines.forEach(line -> messages.send(viewer, line));
            }
        }
    }

    /** 语言文本不含 PAPI 变量时按语言渲染一次并广播，避免每名玩家重复解析。 */
    private boolean rankingTextsUsePapi(String locale) {
        return locales.text(locale, "boss.ranking-header").indexOf('%') >= 0
                || locales.text(locale, "boss.ranking-entry").indexOf('%') >= 0
                || locales.text(locale, "boss.ranking-hover").indexOf('%') >= 0
                || locales.text(locale, "boss.reward-line").indexOf('%') >= 0;
    }

    private List<Component> buildRankingLines(
            Player context,
            String locale,
            BossFight fight,
            List<Map.Entry<UUID, Double>> ranking,
            Map<UUID, List<RewardGrant>> awarded) {
        List<Component> lines = new ArrayList<>();
        lines.add(messages.renderKey(locale, "boss.ranking-header", context,
                Map.of("boss", fight.config.display(locale))));
        for (int index = 0; index < ranking.size(); index++) {
            Map.Entry<UUID, Double> damage = ranking.get(index);
            String name = fight.playerNames.getOrDefault(damage.getKey(), damage.getKey().toString());
            StringBuilder rewardLines = new StringBuilder();
            for (RewardGrant grant : awarded.getOrDefault(damage.getKey(), List.of())) {
                rewardLines.append(localizedRewardLine(context, locale, grant)).append('\n');
            }
            if (!rewardLines.isEmpty()) {
                rewardLines.setLength(rewardLines.length() - 1);
            }
            Component hover = messages.renderKey(locale, "boss.ranking-hover", context, Map.of(
                    "player", name,
                    "damage", DAMAGE_FORMAT.format(damage.getValue()),
                    "rewards", rewardLines));
            lines.add(messages.renderKey(locale, "boss.ranking-entry", context, Map.of(
                    "rank", index + 1,
                    "player", name,
                    "damage", DAMAGE_FORMAT.format(damage.getValue()))).hoverEvent(hover));
        }
        return lines;
    }

    private String localizedRewardLine(Player context, String locale, RewardGrant grant) {
        Component component = messages.renderKey(locale, "boss.reward-line", context, Map.of(
                "reward", rewards.rewardName(grant, locale),
                "amount", grant.amount(),
                "rarity", rewards.rarityName(grant, locale)));
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(component);
    }

    private void broadcast(BossConfig boss, BossBroadcast broadcast, Location location, Player killer) {
        Map<String, Object> variables = Map.of(
                "boss", boss.id(),
                "killer", killer == null ? "" : killer.getName(),
                "location", formatLocation(location));
        Map<String, Component> sharedByLocale = new HashMap<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            String locale = locales.locale(viewer);
            String raw = broadcast.message(locale);
            if (raw.isBlank()) {
                continue;
            }
            if (raw.indexOf('%') >= 0) {
                messages.send(viewer, messages.render(raw, viewer, variables));
            } else {
                messages.send(viewer, sharedByLocale.computeIfAbsent(
                        locale, ignored -> messages.render(raw, null, variables)));
            }
        }
        rewards.executeConsoleCommands(broadcast.commands(), killer, variables);
    }

    private void removeFight(BossFight fight) {
        fights.remove(fight);
    }

    /** 回收被其他插件移除或因区块卸载而不再由 MythicMobs 跟踪的战斗。 */
    void sweepInactiveFights() {
        fights.sweep(mythicMobs::isLoadedAndActive);
    }

    private int activeAtSpawner(String spawnerKey) {
        return fights.spawnerCount(spawnerKey);
    }

    private static boolean hasEnoughOnlinePlayers(BossSpawner spawner) {
        return Bukkit.getOnlinePlayers().size() >= spawner.minimumOnlinePlayers();
    }

    private Optional<BossFight> firstFight(String bossId) {
        return fights.firstBoss(bossId);
    }

    private Optional<LivingEntity> firstLivingEntity(String bossId) {
        return firstFight(bossId).map(fight -> Bukkit.getEntity(fight.entityId))
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast);
    }

    private static Player resolvePlayer(Entity source) {
        if (source instanceof Player player) {
            return player;
        }
        if (source instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        if (source instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        if (source instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static String key(BossConfig boss, BossSpawner spawner) {
        return boss.id() + ":" + spawner.id();
    }

    private static String formatLocation(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }

    private record AttemptKey(UUID playerId, String bossId, String spawnerId) {
    }
}
