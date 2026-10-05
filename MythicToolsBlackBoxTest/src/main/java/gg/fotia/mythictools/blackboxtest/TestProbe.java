package gg.fotia.mythictools.blackboxtest;

import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.integration.MythicMobsAdapter;
import gg.fotia.mythictools.item.ItemFactory;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** 提供 BlackBoxPro 可触发、可查询且可回滚的测试操作。 */
final class TestProbe extends TestActions {
    private final MythicToolsBlackBoxTestPlugin plugin;
    private final MythicToolsPlugin target;
    private final MythicMobsAdapter mythicMobs = new MythicMobsAdapter();
    private final FixtureManager fixtures;
    private final TestMessages messages;
    private final Map<String, Integer> markers;
    private final Map<UUID, PermissionAttachment> permissions;
    private final ProbeState state = new ProbeState();
    private final PendingRewardSqlStore pendingStore;
    private final NamespacedKey pendingFixtureKey;

    TestProbe(
            MythicToolsBlackBoxTestPlugin plugin,
            MythicToolsPlugin target,
            FixtureManager fixtures,
            TestMessages messages,
            Map<String, Integer> markers,
            Map<UUID, PermissionAttachment> permissions) {
        this.plugin = plugin;
        this.target = target;
        this.fixtures = fixtures;
        this.messages = messages;
        this.markers = markers;
        this.permissions = permissions;
        this.pendingStore = new PendingRewardSqlStore(
                () -> new File(target.getDataFolder(), target.settings().databaseFile()).toPath(),
                new File(plugin.getDataFolder(), "pending-corrupt-ownership.txt").toPath());
        this.pendingFixtureKey = new NamespacedKey(plugin, "pending_reward_fixture");
    }

    FixtureManager fixtures() {
        return fixtures;
    }

    void fixture(Player player, String rawMode) {
        String mode = rawMode.toLowerCase(java.util.Locale.ROOT);
        if (mode.equals("restore") || mode.equals("clear")) {
            if (!clearPendingFixtures(player)) {
                output(player, "FIXTURE", Map.of(
                        "mode", mode,
                        "prepared", false,
                        "reason", "pending-clear-failed"));
                return;
            }
            state.clear();
            fixtures.restore(player);
            return;
        }
        if (!fixtures.modes().contains(mode)) {
            throw new IllegalArgumentException("unknown-fixture-" + mode);
        }
        if (!fixtures.apply(player, mode)) {
            output(player, "FIXTURE", Map.of("mode", mode, "prepared", false));
            return;
        }
        state.activateFixture(mode);
        switch (mode) {
            case "reward-offline" -> preparePendingFixture(player, false, false);
            case "reward-overflow" -> preparePendingFixture(player, true, false);
            case "corrupt-pending" -> preparePendingFixture(player, false, true);
            case "chat-await" -> {
                target.adminGui().openMain(player);
                output(player, "FIXTURE", Map.of(
                        "mode", mode,
                        "prepared", true,
                        "next", "navigate-to-chat-input"));
                messages.send(player, "ready", Map.of("mode", mode));
            }
            default -> output(player, "FIXTURE", Map.of("mode", mode, "prepared", true));
        }
    }

    void snapshot(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length; index++) {
            yaml.set("inventory." + index, contents[index]);
        }
        yaml.set("inventory-size", contents.length);
        yaml.set("level", player.getLevel());
        yaml.set("exp", player.getExp());
        yaml.set("total-exp", player.getTotalExperience());
        yaml.set("health", player.getHealth());
        yaml.set("food", player.getFoodLevel());
        yaml.set("game-mode", player.getGameMode().name());
        setLocation(yaml, "location", player.getLocation());
        try {
            yaml.save(snapshotFile(player));
            messages.send(player, "snapshot", Map.of());
        } catch (IOException exception) {
            messages.send(player, "invalid", Map.of("reason", "snapshot-io"));
        }
    }

    void reset(Player player) {
        moveToIsolatedTestArea(player);
        removeTestEntities(player);
        player.getInventory().clear();
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0.0f);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setGameMode(GameMode.CREATIVE);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        markers.clear();
        messages.send(player, "reset", Map.of());
    }

    void restore(Player player) {
        removeTestEntities(player);
        boolean pendingCleared = clearPendingFixtures(player);
        boolean snapshotRestored = restoreSnapshot(player);
        if (!pendingCleared || !snapshotRestored) {
            output(player, "RESTORE", Map.of(
                    "success", false,
                    "pendingCleared", pendingCleared,
                    "snapshotRestored", snapshotRestored));
            return;
        }
        permissions.computeIfPresent(player.getUniqueId(), (ignored, attachment) -> {
            attachment.remove();
            return null;
        });
        markers.clear();
        state.clear();
        fixtures.restore(player);
    }

    void spawn(Player player, String mobId) {
        Location location = player.getLocation().clone().add(2.0, 0.0, 0.0);
        ActiveMob mob = MythicBukkit.inst().getMobManager().spawnMob(mobId, location, 1.0);
        if (mob == null) {
            output(player, "SPAWN", Map.of("success", false, "mob", mobId));
            return;
        }
        mob.getEntity().setSaveToDisk(false);
        output(player, "SPAWN", Map.of("success", true, "mob", mobId, "uuid", mob.getUniqueId()));
    }

    void damage(Player player, double amount) {
        LivingEntity entity = nearestMythic(player, null);
        if (entity == null) {
            output(player, "DAMAGE", Map.of("success", false));
            return;
        }
        entity.damage(amount, player);
        output(player, "DAMAGE", Map.of("success", true, "amount", amount, "uuid", entity.getUniqueId()));
    }

    void kill(Player player, String attribution, String mobId) {
        LivingEntity entity = nearestMythic(player, mobId);
        if (entity == null) {
            output(player, "KILL", Map.of("success", false, "mob", mobId == null ? "any" : mobId));
            return;
        }
        if (attribution.equalsIgnoreCase("environment")) {
            entity.setHealth(0.0);
        } else {
            entity.damage(Math.max(2048.0, entity.getHealth() + 100.0), player);
        }
        output(player, "KILL", Map.of(
                "success", true,
                "attribution", attribution,
                "uuid", entity.getUniqueId()));
    }

    void scheduleKill(Player player, int ticks) {
        LivingEntity entity = nearestMythic(player, null);
        if (entity == null) {
            output(player, "SCHEDULE_KILL", Map.of("success", false));
            return;
        }
        UUID entityId = entity.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity current = Bukkit.getEntity(entityId);
            if (current instanceof LivingEntity living && !living.isDead()) {
                living.setHealth(0.0);
                plugin.getLogger().info("MTTEST_SCHEDULE_KILL executed uuid=" + entityId);
            }
        }, Math.max(1, ticks));
        output(player, "SCHEDULE_KILL", Map.of("success", true, "ticks", ticks, "uuid", entityId));
    }

    void triggerPoint(Player player) {
        boolean triggered = target.spawningManager().triggerPoint("qa-point");
        output(player, "POINT", Map.of(
                "triggered", triggered,
                "alive", target.spawningManager().aliveAtPoint("qa-point")));
    }

    void spawnBoss(Player player) {
        boolean spawned = target.bossManager().spawnNow(
                "qa-boss", player.getLocation().clone().add(3.0, 0.0, 0.0));
        output(player, "BOSS", Map.of(
                "spawned", spawned,
                "count", target.bossManager().aliveCount("qa-boss"),
                "phase", target.bossManager().currentPhase("qa-boss")));
    }

    void placeholder(Player player, String params) {
        String value = PlaceholderAPI.setPlaceholders(player, "%mythictools_" + params + "%");
        output(player, "PAPI", Map.of("params", params, "value", value));
    }

    void asyncPlaceholder(Player player, String params) {
        String placeholder = "%mythictools_" + params + "%";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String first = PlaceholderAPI.setPlaceholders(player, placeholder);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        String second = PlaceholderAPI.setPlaceholders(player, placeholder);
                        Bukkit.getScheduler().runTask(plugin, () -> output(player, "PAPI_ASYNC", Map.of(
                                "params", params,
                                "first", first,
                                "second", second)));
                    }), 2L);
        });
    }

    void render(Player player, String fixture) {
        String input = switch (fixture.toLowerCase(java.util.Locale.ROOT)) {
            case "minimessage" -> "<!i><green>MTQA_MINIMESSAGE";
            case "legacy-ampersand" -> "&aMTQA_LEGACY_AMPERSAND";
            case "legacy-section" -> "\u00a7aMTQA_LEGACY_SECTION";
            case "legacy-hex" -> "&#12AB34MTQA_LEGACY_HEX";
            case "craftengine-image" -> "MTQA_IMAGE <image:internal:item_browser>";
            default -> throw new IllegalArgumentException("unknown-render-fixture-" + fixture);
        };
        target.messages().send(player, target.messages().render(input, player, Map.of()));
    }

    void visualMetadata(Player player) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("material", "DIAMOND");
        config.set("item-model", "mythictools:probe_item_model");
        config.set("tooltip-style", "mythictools:probe_tooltip_style");
        ItemStack item = new ItemFactory(target.messages(), target.serverVersion(), plugin.getLogger())
                .create(config, player, Map.of());
        player.getInventory().setItem(8, item);
        output(player, "VISUAL", Map.of(
                "itemModelAndTooltipStyle", target.serverVersion().supportsItemModelAndTooltipStyle(),
                "slot", 8));
    }

    void captureGuiVisualMetadata(Player player) {
        target.adminGui().openMain(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack guiItem = player.getOpenInventory().getTopInventory().getItem(10);
            if (guiItem == null || guiItem.getType().isAir()) {
                output(player, "GUI_VISUAL", Map.of("captured", false));
                return;
            }
            player.getInventory().setItem(7, guiItem.clone());
            output(player, "GUI_VISUAL", Map.of("captured", true, "slot", 7));
        });
    }

    void mark(CommandSender sender, String key) {
        int value = markers.merge(key, 1, Integer::sum);
        output(sender, "MARK", Map.of("key", key, "count", value));
    }

    void permission(Player player, String node, String rawValue) {
        PermissionAttachment attachment = permissions.computeIfAbsent(
                player.getUniqueId(), ignored -> player.addAttachment(plugin));
        if (rawValue.equalsIgnoreCase("clear")) {
            attachment.unsetPermission(node);
        } else {
            attachment.setPermission(node, Boolean.parseBoolean(rawValue));
        }
        player.recalculatePermissions();
        output(player, "PERMISSION", Map.of("node", node, "value", rawValue));
    }

    void state(Player player) {
        Map<String, Integer> inventory = countInventory(player);
        Map<String, Integer> dropped = new java.util.TreeMap<>();
        Map<String, Integer> mobs = new java.util.TreeMap<>();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity.getLocation().distanceSquared(player.getLocation()) > 64.0 * 64.0) {
                continue;
            }
                if (entity instanceof Item item) {
                    dropped.merge(item.getItemStack().getType().name(), item.getItemStack().getAmount(), Integer::sum);
                }
                mythicMobs.mobId(entity)
                        .filter(id -> id.startsWith("MTQA_"))
                        .ifPresent(id -> mobs.merge(id, 1, Integer::sum));
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("inventory", compact(inventory));
        values.put("dropped", compact(dropped));
        values.put("mobs", compact(mobs));
        values.put("level", player.getLevel());
        values.put("totalExp", player.getTotalExperience());
        values.put("runtimeReady", runtimeReady());
        values.put("fixture", state.activeFixture());
        values.put("snapshot", snapshotSignature());
        values.put("pointAlive", safePointAlive());
        values.put("managedSpawnAlive", safePointAlive());
        values.put("bossCount", safeBossAlive());
        values.put("bossAlive", safeBossAlive());
        values.put("bossPhase", safeBossPhase());
        values.put("chatAwaiting", safeChatAwaiting(player.getUniqueId()));
        PendingRewardCounts pending = inspectPending(player.getUniqueId());
        values.put("pending", pending.pending());
        values.put("quarantine", pending.quarantine());
        values.put("testPending", pending.testPending());
        values.put("testQuarantine", pending.testQuarantine());
        ProbeState.ReloadState reload = state.lastReload();
        values.put("lastReload", reload.status());
        values.put("reloadPreserved", reload.preserved());
        values.put("reloadIdentityPreserved", reload.identityPreserved());
        values.put("markers", compact(new java.util.TreeMap<>(markers)));
        output(player, "STATE", values);
    }

    void reload(Player player) {
        String before = snapshotSignature();
        Object beforeSettings = target.settings();
        Object beforeSpawningRepository = target.spawningRepository();
        Object beforeBossRepository = target.bossRepository();
        Object beforeSpawningManager = target.spawningManager();
        Object beforeBossManager = target.bossManager();
        Object beforeAdminGui = target.adminGui();
        Object beforeMessages = target.messages();
        Object beforeLocales = target.locales();
        boolean success = false;
        String reason = "none";
        try {
            target.reloadRuntime();
            success = runtimeReady();
            if (!success) {
                reason = "runtime-not-ready";
            }
        } catch (RuntimeException exception) {
            reason = exception.getClass().getSimpleName() + ":" + String.valueOf(exception.getMessage());
            plugin.getLogger().log(java.util.logging.Level.INFO,
                    "MTTEST 捕获到预期或非预期的正式重载失败", exception);
        }
        String after = snapshotSignature();
        boolean identityPreserved = beforeSettings == target.settings()
                && beforeSpawningRepository == target.spawningRepository()
                && beforeBossRepository == target.bossRepository()
                && beforeSpawningManager == target.spawningManager()
                && beforeBossManager == target.bossManager()
                && beforeAdminGui == target.adminGui()
                && beforeMessages == target.messages()
                && beforeLocales == target.locales();
        state.recordReload(success, before, after, reason, identityPreserved);
        output(player, "RELOAD", Map.of(
                "success", success,
                "runtimeReady", runtimeReady(),
                "before", before,
                "after", after,
                "preserved", before.equals(after),
                "identityPreserved", identityPreserved,
                "reason", reason));
    }

    void remove(Player player, String targetName) {
        Entity entity = findRemovalTarget(player, targetName);
        if (entity == null) {
            output(player, "REMOVE", Map.of("success", false, "target", targetName));
            return;
        }
        String mythicId = mythicMobs.mobId(entity).orElse("unknown");
        UUID entityId = entity.getUniqueId();
        entity.remove();
        output(player, "REMOVE", Map.of(
                "success", true,
                "target", targetName,
                "mob", mythicId,
                "uuid", entityId));
    }

    void pending(Player player, String[] args) {
        String action = args.length == 0 ? "inspect" : args[0].toLowerCase(java.util.Locale.ROOT);
        try {
            switch (action) {
                case "clear" -> pendingStore.clear(player.getUniqueId(), this::isTestRewardPayload);
                case "seed" -> {
                    Material material = args.length >= 2
                            ? Material.matchMaterial(args[1]) : Material.EMERALD;
                    if (material == null || material.isAir()) {
                        throw new IllegalArgumentException("invalid-material-" + (args.length >= 2 ? args[1] : "null"));
                    }
                    int amount = args.length >= 3 ? Integer.parseInt(args[2]) : 2;
                    if (amount < 1 || amount > 64) {
                        throw new IllegalArgumentException("amount-out-of-range");
                    }
                    pendingStore.seed(player.getUniqueId(), List.of(serialize(testRewardItem(material, amount))));
                }
                case "corrupt" -> pendingStore.seedCorruptPair(
                        player.getUniqueId(), serialize(testRewardItem(Material.DIAMOND, 2)));
                case "inspect" -> {
                    // 只读操作在统一输出阶段执行。
                }
                default -> throw new IllegalArgumentException("unknown-pending-action-" + action);
            }
            PendingRewardCounts counts = pendingStore.inspect(player.getUniqueId(), this::isTestRewardPayload);
            output(player, "PENDING", Map.of(
                    "action", action,
                    "success", true,
                    "pending", counts.pending(),
                    "quarantine", counts.quarantine(),
                    "testPending", counts.testPending(),
                    "testQuarantine", counts.testQuarantine()));
        } catch (SQLException | IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "MTTEST 待领取奖励操作失败", exception);
            output(player, "PENDING", Map.of(
                    "action", action,
                    "success", false,
                    "reason", exception.getClass().getSimpleName()));
        }
    }

    void assertCase(Player player, String rawCase) {
        String testCase = rawCase.toLowerCase(java.util.Locale.ROOT);
        PendingRewardCounts counts = inspectPending(player.getUniqueId());
        boolean passed = switch (testCase) {
            case "runtime" -> runtimeReady();
            case "rollback" -> !state.lastReload().success()
                    && state.lastReload().preserved()
                    && state.lastReload().identityPreserved()
                    && runtimeReady();
            case "external-remove" -> safePointAlive() == 0 && safeBossAlive() == 0;
            case "pending-corrupt" -> counts.testPending() == 0 && counts.testQuarantine() >= 1;
            case "pending-clear" -> counts.testPending() == 0 && counts.testQuarantine() == 0;
            case "chat-cleared" -> !safeChatAwaiting(player.getUniqueId());
            default -> throw new IllegalArgumentException("unknown-assert-case-" + testCase);
        };
        player.sendMessage("MTTEST " + (passed ? "PASS" : "FAIL") + " " + testCase
                + " runtimeReady=" + runtimeReady()
                + " pointAlive=" + safePointAlive()
                + " bossAlive=" + safeBossAlive()
                + " chatAwaiting=" + safeChatAwaiting(player.getUniqueId())
                + " testPending=" + counts.testPending()
                + " testQuarantine=" + counts.testQuarantine());
    }

    private void preparePendingFixture(Player player, boolean fillInventory, boolean corrupt) {
        try {
            pendingStore.clear(player.getUniqueId(), this::isTestRewardPayload);
            if (fillInventory) {
                ItemStack[] storage = player.getInventory().getStorageContents();
                Arrays.fill(storage, new ItemStack(Material.COBBLESTONE, 64));
                player.getInventory().setStorageContents(storage);
            }
            if (corrupt) {
                pendingStore.seedCorruptPair(
                        player.getUniqueId(), serialize(testRewardItem(Material.DIAMOND, 2)));
            } else {
                pendingStore.seed(
                        player.getUniqueId(), List.of(serialize(testRewardItem(Material.EMERALD, 2))));
            }
            PendingRewardCounts counts = pendingStore.inspect(player.getUniqueId(), this::isTestRewardPayload);
            output(player, "FIXTURE", Map.of(
                    "mode", state.activeFixture(),
                    "prepared", true,
                    "inventoryFull", fillInventory,
                    "testPending", counts.testPending(),
                    "corrupt", corrupt));
            messages.send(player, "ready", Map.of("mode", state.activeFixture()));
        } catch (SQLException | IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "MTTEST 待领取奖励夹具准备失败", exception);
            output(player, "FIXTURE", Map.of(
                    "mode", state.activeFixture(),
                    "prepared", false,
                    "reason", exception.getClass().getSimpleName()));
            messages.send(player, "invalid", Map.of("reason", exception.getClass().getSimpleName()));
        }
    }

    private boolean clearPendingFixtures(Player player) {
        try {
            pendingStore.clear(player.getUniqueId(), this::isTestRewardPayload);
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "MTTEST 测试待领取记录清理失败", exception);
            return false;
        }
    }

    private PendingRewardCounts inspectPending(UUID playerId) {
        try {
            return pendingStore.inspect(playerId, this::isTestRewardPayload);
        } catch (SQLException exception) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "MTTEST 无法读取待领取奖励统计", exception);
            return new PendingRewardCounts(-1, -1, -1, -1);
        }
    }

    private static byte[] serialize(ItemStack item) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream objectOutput = new BukkitObjectOutputStream(output)) {
            objectOutput.writeObject(item);
            objectOutput.flush();
            return output.toByteArray();
        }
    }

    private ItemStack testRewardItem(Material material, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(pendingFixtureKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isTestRewardPayload(byte[] payload) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(payload);
             BukkitObjectInputStream objectInput = new BukkitObjectInputStream(input)) {
            Object value = objectInput.readObject();
            if (!(value instanceof ItemStack item) || !item.hasItemMeta()) {
                return false;
            }
            return item.getItemMeta().getPersistentDataContainer()
                    .has(pendingFixtureKey, PersistentDataType.BYTE);
        } catch (IOException | ClassNotFoundException | RuntimeException exception) {
            return false;
        }
    }

    private boolean runtimeReady() {
        try {
            return target.isEnabled()
                    && target.settings() != null
                    && target.spawningRepository() != null
                    && target.bossRepository() != null
                    && target.spawningManager() != null
                    && target.bossManager() != null
                    && target.adminGui() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String snapshotSignature() {
        try {
            String points = target.spawningRepository().spawnPointIds().stream().sorted()
                    .collect(Collectors.joining(","));
            String groups = target.spawningRepository().mobGroupIds().stream().sorted()
                    .collect(Collectors.joining(","));
            String bosses = target.bossRepository().bossIds().stream().sorted()
                    .collect(Collectors.joining(","));
            return "points:" + points + "|groups:" + groups + "|bosses:" + bosses;
        } catch (RuntimeException exception) {
            return "unavailable";
        }
    }

    private Entity findRemovalTarget(Player player, String rawTarget) {
        String requested = rawTarget.toLowerCase(java.util.Locale.ROOT);
        UUID requestedId = null;
        try {
            requestedId = UUID.fromString(rawTarget);
        } catch (IllegalArgumentException ignored) {
            // 不是 UUID 时按测试实体类别或 MythicMob ID 匹配。
        }
        UUID exactId = requestedId;
        return player.getWorld().getEntities().stream()
                .filter(entity -> mythicMobs.mobId(entity)
                        .map(id -> id.startsWith("MTQA_")).orElse(false))
                .filter(entity -> {
                    if (exactId != null) {
                        return entity.getUniqueId().equals(exactId);
                    }
                    String mythicId = mythicMobs.mobId(entity).orElse("");
                    if (requested.equals("boss")) {
                        return isBossTestMob(mythicId);
                    }
                    if (requested.equals("mob")) {
                        return !isBossTestMob(mythicId);
                    }
                    return mythicId.equalsIgnoreCase(rawTarget);
                })
                .min(Comparator.comparingDouble(entity ->
                        entity.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private static boolean isBossTestMob(String mythicId) {
        return mythicId.startsWith("MTQA_Boss") || mythicId.equals("MTQA_NativeBoss");
    }

    private int safePointAlive() {
        try {
            return target.spawningManager() == null ? -1 : target.spawningManager().aliveAtPoint("qa-point");
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private int safeBossAlive() {
        try {
            return target.bossManager() == null ? -1 : target.bossManager().aliveCount("qa-boss");
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private boolean safeChatAwaiting(UUID playerId) {
        try {
            return target.isChatInputAwaiting(playerId);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Object safeBossPhase() {
        try {
            return target.bossManager() == null ? "unavailable" : target.bossManager().currentPhase("qa-boss");
        } catch (RuntimeException exception) {
            return "unavailable";
        }
    }

    private boolean restoreSnapshot(Player player) {
        File file = snapshotFile(player);
        if (!file.isFile()) {
            messages.send(player, "invalid", Map.of("reason", "snapshot-missing"));
            return false;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            int size = yaml.getInt("inventory-size", player.getInventory().getContents().length);
            ItemStack[] contents = new ItemStack[size];
            for (int index = 0; index < size; index++) {
                contents[index] = yaml.getItemStack("inventory." + index);
            }
            player.getInventory().setContents(contents);
            player.setTotalExperience(0);
            player.setLevel(yaml.getInt("level"));
            player.setExp((float) yaml.getDouble("exp"));
            player.setTotalExperience(yaml.getInt("total-exp"));
            player.setHealth(Math.min(yaml.getDouble("health", player.getMaxHealth()), player.getMaxHealth()));
            player.setFoodLevel(yaml.getInt("food", 20));
            player.setGameMode(GameMode.valueOf(yaml.getString("game-mode", "SURVIVAL")));
            Location location = readLocation(yaml, "location");
            if (location != null) {
                player.teleport(location);
            }
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "MTTEST 玩家快照恢复失败", exception);
            messages.send(player, "invalid", Map.of("reason", "snapshot-invalid"));
            return false;
        }
    }

    private void removeTestEntities(Player player) {
        List<Entity> entities = Bukkit.getWorlds().stream().flatMap(world -> world.getEntities().stream()).toList();
        for (Entity entity : entities) {
            mythicMobs.mobId(entity)
                    .filter(id -> id.startsWith("MTQA_"))
                    .ifPresent(ignored -> {
                        Optional<ActiveMob> activeMob =
                                MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
                        TestEntityCleanup.remove(
                                () -> activeMob.ifPresent(ActiveMob::remove),
                                entity::remove);
                    });
            if ((entity instanceof Item || entity instanceof ExperienceOrb)
                    && entity.getWorld().equals(player.getWorld())
                    && entity.getLocation().distanceSquared(player.getLocation()) <= 64.0 * 64.0) {
                entity.remove();
            }
        }
    }

    private static void moveToIsolatedTestArea(Player player) {
        World world = player.getWorld();
        int x = 10_000;
        int z = 10_000;
        world.getChunkAt(x >> 4, z >> 4).load();
        int y = world.getHighestBlockYAt(x, z) + 1;
        player.teleport(new Location(world, x + 0.5, y, z + 0.5));
    }

    private LivingEntity nearestMythic(Player player, String mobId) {
        return player.getWorld().getEntities().stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(entity -> mythicMobs.mobId(entity)
                        .map(id -> id.startsWith("MTQA_")
                                && (mobId == null || id.equalsIgnoreCase(mobId))).orElse(false))
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private static Map<String, Integer> countInventory(Player player) {
        Map<String, Integer> result = new java.util.TreeMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                result.merge(item.getType().name(), item.getAmount(), Integer::sum);
            }
        }
        return result;
    }

    private static String compact(Map<String, Integer> values) {
        return values.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private void output(CommandSender sender, String type, Map<String, ?> values) {
        sender.sendMessage(MachineOutput.format(type, values));
    }

    private File snapshotFile(Player player) {
        File directory = new File(plugin.getDataFolder(), "snapshots");
        directory.mkdirs();
        return new File(directory, player.getUniqueId() + ".yml");
    }

    private static void setLocation(YamlConfiguration yaml, String path, Location location) {
        yaml.set(path + ".world", location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }

    private static Location readLocation(YamlConfiguration yaml, String path) {
        World world = Bukkit.getWorld(yaml.getString(path + ".world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world,
                yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"), yaml.getDouble(path + ".z"),
                (float) yaml.getDouble(path + ".yaw"), (float) yaml.getDouble(path + ".pitch"));
    }
}
