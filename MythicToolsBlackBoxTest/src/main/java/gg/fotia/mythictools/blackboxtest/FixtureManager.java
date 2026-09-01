package gg.fotia.mythictools.blackboxtest;

import gg.fotia.mythictools.MythicToolsPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** 创建和清理只使用 qa-* 命名空间的确定性测试配置。 */
final class FixtureManager {
    private static final List<String> DROP_MODES = List.of(
            "drops-ground", "drops-inventory", "drops-console", "drops-player",
            "drops-max", "drops-weighted", "drops-first-player", "drops-first-server");
    private static final List<String> POINT_MODES = List.of(
            "spawn-point", "spawn-point-auto", "spawn-point-amount",
            "spawn-point-death", "spawn-point-despawn");
    private static final List<String> BIOME_MODES = List.of(
            "spawn-biome", "spawn-biome-amount", "spawn-biome-limit", "spawn-biome-despawn",
            "spawn-biome-blocked", "spawn-biome-world-blocked", "spawn-biome-biome-blocked",
            "spawn-biome-height-blocked", "spawn-biome-light-blocked", "spawn-biome-distance");
    private static final List<String> BOSS_MODES = List.of(
            "boss-manual", "boss-point", "boss-biome", "boss-biome-chance-blocked",
            "boss-biome-world-blocked", "boss-biome-biome-blocked", "boss-offline", "boss-invalid",
            "boss-point-time-window", "boss-point-online-blocked", "boss-biome-online-blocked",
            "boss-phase-respawn", "boss-native", "boss-loot-intermediate-none",
            "boss-loot-intermediate-mythic", "boss-loot-mythictools-only",
            "boss-loot-mythic-only", "boss-loot-combined",
            "boss-first-player", "boss-first-server");
    private static final List<String> PREPARED_RELOAD_MODES = List.of(
            "valid-reload", "invalid-reload", "limit-overflow", "reward-overflow");
    private static final List<String> ACTION_ONLY_MODES = List.of(
            "reward-offline", "corrupt-pending", "chat-await");

    private final MythicToolsBlackBoxTestPlugin plugin;
    private final MythicToolsPlugin target;
    private final TestMessages messages;
    private final FixtureCatalog catalog;
    private final FileBackupSession fixtureBackups;
    private BukkitTask pendingReloadTask;
    private long reloadGeneration;

    FixtureManager(
            MythicToolsBlackBoxTestPlugin plugin,
            MythicToolsPlugin target,
            TestMessages messages) {
        this.plugin = plugin;
        this.target = target;
        this.messages = messages;
        this.catalog = new FixtureCatalog(java.util.stream.Stream.of(
                        DROP_MODES, POINT_MODES, BIOME_MODES, BOSS_MODES)
                .flatMap(List::stream).toList());
        this.fixtureBackups = new FileBackupSession(
                new File(plugin.getDataFolder(), "backups/fixture-session").toPath());
    }

    List<String> modes() {
        return catalog.modes();
    }

    boolean apply(Player player, String rawMode) {
        String requestedMode = rawMode.toLowerCase(Locale.ROOT);
        if (!modes().contains(requestedMode)) {
            messages.send(player, "invalid", Map.of("reason", "unknown-fixture-" + requestedMode));
            return false;
        }
        invalidatePendingReload();
        String mode = catalog.canonical(requestedMode);
        try {
            if (ACTION_ONLY_MODES.contains(requestedMode)) {
                return true;
            }
            fixtureBackups.begin(fixtureFiles());
            ensureMythicMobFixture();
            cleanQaTargetConfigs();
            if (PREPARED_RELOAD_MODES.contains(requestedMode)) {
                writePreparedReloadFixture(player, requestedMode);
                if (!requestedMode.equals("reward-overflow")) {
                    reloadMythicMobsOnly(player, requestedMode);
                }
                return true;
            } else if (DROP_MODES.contains(mode)) {
                writeDropFixture(mode);
            } else if (POINT_MODES.contains(mode)) {
                writePointFixture(player, mode);
            } else if (BIOME_MODES.contains(mode)) {
                writeBiomeFixture(player, mode);
            } else {
                writeBossFixture(player, mode);
            }
            reloadAfterMythicMobs(player, requestedMode, false, false);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "无法写入测试夹具", exception);
            messages.send(player, "invalid", Map.of("reason", exception.getClass().getSimpleName()));
            return false;
        }
    }

    void restore(CommandSender sender) {
        invalidatePendingReload();
        try {
            // 备份会话知道每个路径原先“存在或缺失”，直接恢复才能保证重复执行不删用户文件。
            fixtureBackups.restore(fixtureFiles());
            boolean restoredRuntimeSettings = restoreTargetConfig();
            reloadAfterMythicMobs(sender, "restore", true, restoredRuntimeSettings);
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "无法清理测试夹具", exception);
            messages.send(sender, "invalid", Map.of("reason", exception.getClass().getSimpleName()));
        }
    }

    private void ensureMythicMobFixture() throws IOException {
        File targetFile = mythicMobFixture();
        Files.createDirectories(targetFile.getParentFile().toPath());
        try (InputStream stream = plugin.getResource("fixtures/MythicToolsQA.yml")) {
            if (stream == null) {
                throw new IOException("JAR 缺少 MythicToolsQA.yml");
            }
            Files.copy(stream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File mythicMobFixture() {
        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs == null) {
            throw new IllegalStateException("MythicMobs 未加载");
        }
        return new File(mythicMobs.getDataFolder(), "mobs/MythicToolsQA.yml");
    }

    private void writeDropFixture(String mode) throws IOException {
        String groupId = "qa-" + mode.substring("drops-".length());
        YamlConfiguration group = new YamlConfiguration();
        boolean firstDefeat = mode.startsWith("drops-first-");
        if (firstDefeat) {
            group.set("entries.reward.type", "item");
            group.set("entries.reward.grant-mode", "first-defeat");
            group.set("entries.reward.material", "NETHER_STAR");
            group.set("entries.reward.min-amount", 1);
            group.set("entries.reward.max-amount", 1);
            group.set("entries.reward.rarity", "epic");
            group.set("entries.reward.delivery", "inventory");
            group.set("entries.reward.display.zh_CN", "<!i><light_purple>QA 首次击败奖励");
            group.set("entries.reward.display.en_US", "<!i><light_purple>QA First Defeat Reward");
            group.set("entries.reward.message.zh_CN", "<!i><green>MTQA_FIRST_DEFEAT {reward}");
            group.set("entries.reward.message.en_US", "<!i><green>MTQA_FIRST_DEFEAT {reward}");
        } else if (mode.equals("drops-weighted")) {
            writeCommandEntry(group, "entries.low", "mttest mark weighted-low", 1, "console", 1);
            writeCommandEntry(group, "entries.high", "mttest mark weighted-high", 3, "console", 1);
        } else if (mode.equals("drops-console") || mode.equals("drops-player")) {
            String executor = mode.equals("drops-player") ? "player" : "console";
            writeCommandEntry(group, "entries.reward", "mttest mark " + executor, 1, executor, 2);
        } else {
            String material = mode.equals("drops-inventory") ? "EMERALD" : "DIAMOND";
            String delivery = mode.equals("drops-inventory") ? "inventory" : "ground";
            group.set("entries.reward.type", "item");
            group.set("entries.reward.material", material);
            group.set("entries.reward.weight", 1);
            group.set("entries.reward.min-amount", mode.equals("drops-max") ? 1 : 2);
            group.set("entries.reward.max-amount", mode.equals("drops-max") ? 1 : 2);
            group.set("entries.reward.rarity", "common");
            group.set("entries.reward.delivery", delivery);
            group.set("entries.reward.display.zh_CN", "<!i><aqua>QA 奖励");
            group.set("entries.reward.display.en_US", "<!i><aqua>QA Reward");
            group.set("entries.reward.message.zh_CN", "<!i><green>MTQA_REWARD {reward} x{amount} [{rarity}]");
            group.set("entries.reward.message.en_US", "<!i><green>MTQA_REWARD {reward} x{amount} [{rarity}]");
        }
        save(group, new File(target.getDataFolder(), "drops/groups/" + groupId + ".yml"));

        YamlConfiguration mob = new YamlConfiguration();
        mob.set("mob-id", "MTQA_DropMob");
        mob.set("max-drops", mode.equals("drops-max") ? 2 : 1);
        mob.set("experience.min", 5);
        mob.set("experience.max", 5);
        mob.set("groups", List.of(Map.of(
                "id", groupId,
                "weight", 1,
                "min-amount", mode.equals("drops-max") ? 2 : 1,
                "max-amount", mode.equals("drops-max") ? 2 : 1)));
        if (firstDefeat) {
            mob.set("first-defeat.enabled", true);
            mob.set("first-defeat.scope", mode.equals("drops-first-server") ? "server" : "player");
            mob.set("first-defeat.recipient", "killer");
            mob.set("first-defeat.entries", List.of(Map.of(
                    "group", groupId,
                    "entry", "reward")));
        }
        save(mob, new File(target.getDataFolder(), "drops/mobs/MTQA_DropMob.yml"));
    }

    private void writePreparedReloadFixture(Player player, String mode) throws IOException {
        switch (mode) {
            case "valid-reload" -> writePointFixture(player, "spawn-point");
            case "invalid-reload" -> writeInvalidReloadFixture(player);
            case "limit-overflow" -> writeLimitOverflowFixture();
            case "reward-overflow" -> writeRewardOverflowConfig();
            default -> throw new IllegalArgumentException("unknown-prepared-fixture-" + mode);
        }
    }

    private void writeInvalidReloadFixture(Player player) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("mob", "MTQA_MissingMob");
        setLocation(yaml, "location", player.getLocation().clone().add(3.0, 0.0, 0.0));
        yaml.set("interval-seconds", 3600);
        yaml.set("amount.min", 1);
        yaml.set("amount.max", 1);
        yaml.set("max-alive", 1);
        yaml.set("level", 1.0);
        yaml.set("spawn-on-death", false);
        yaml.set("despawn-seconds", 0);
        save(yaml, new File(target.getDataFolder(), "spawning/points/qa-point.yml"));
    }

    private void writeLimitOverflowFixture() throws IOException {
        writeDropFixture("drops-ground");
        File mobFile = new File(target.getDataFolder(), "drops/mobs/MTQA_DropMob.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(mobFile);
        int configuredLimit = target.getConfig().getInt("Safety-Limits.Max-Drops-Per-Mob", 16);
        yaml.set("max-drops", Math.addExact(configuredLimit, 1));
        save(yaml, mobFile);
    }

    private void writeRewardOverflowConfig() throws IOException {
        File config = new File(target.getDataFolder(), "config.yml");
        File backup = targetConfigBackup();
        if (!backup.isFile()) {
            Files.createDirectories(backup.getParentFile().toPath());
            Files.copy(config.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config);
        yaml.set("Rewards.Drop-Overflow-At-Feet", false);
        save(yaml, config);
    }

    private static void writeCommandEntry(
            YamlConfiguration yaml,
            String path,
            String command,
            int weight,
            String executor,
            int amount) {
        yaml.set(path + ".type", "command");
        yaml.set(path + ".command", command);
        yaml.set(path + ".executor", executor);
        yaml.set(path + ".weight", weight);
        yaml.set(path + ".min-amount", amount);
        yaml.set(path + ".max-amount", amount);
        yaml.set(path + ".rarity", "rare");
        yaml.set(path + ".display.zh_CN", "<!i><light_purple>QA 指令奖励");
        yaml.set(path + ".display.en_US", "<!i><light_purple>QA Command Reward");
        yaml.set(path + ".message.zh_CN", "<!i><green>MTQA_COMMAND {reward} x{amount}");
        yaml.set(path + ".message.en_US", "<!i><green>MTQA_COMMAND {reward} x{amount}");
    }

    private void writePointFixture(Player player, String mode) throws IOException {
        Location location = player.getLocation().clone().add(3.0, 0.0, 0.0);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("mob", "MTQA_SpawnMob");
        setLocation(yaml, "location", location);
        boolean amountTwo = mode.equals("spawn-point-amount");
        yaml.set("interval-seconds", mode.equals("spawn-point-auto") ? 1 : 3600);
        yaml.set("amount.min", amountTwo ? 2 : 1);
        yaml.set("amount.max", amountTwo ? 2 : 1);
        yaml.set("max-alive", amountTwo ? 2 : 1);
        yaml.set("level", 1.0);
        yaml.set("spawn-on-death", mode.equals("spawn-point-death"));
        yaml.set("despawn-seconds", mode.equals("spawn-point-despawn") ? 2 : 0);
        save(yaml, new File(target.getDataFolder(), "spawning/points/qa-point.yml"));
    }

    private void writeBiomeFixture(Player player, String mode) throws IOException {
        File file = new File(target.getDataFolder(), "spawning/biomes.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String root = "rules.qa-biome.";
        yaml.set(root + "enabled", true);
        yaml.set(root + "mob", "MTQA_SpawnMob");
        yaml.set(root + "worlds", List.of(mode.equals("spawn-biome-world-blocked")
                ? "mtqa_missing_world" : player.getWorld().getName()));
        String biome = ((Keyed) player.getLocation().getBlock().getBiome())
                .getKey().getKey().toUpperCase(Locale.ROOT);
        yaml.set(root + "biomes", List.of(mode.equals("spawn-biome-biome-blocked")
                ? "NETHER_WASTES" : biome));
        yaml.set(root + "chance", mode.equals("spawn-biome-blocked") ? 0.0 : 1.0);
        yaml.set(root + "interval-seconds", 1);
        yaml.set(root + "min-distance", mode.equals("spawn-biome-distance") ? 5 : 2);
        yaml.set(root + "max-distance", 5);
        int minimumY = player.getWorld().getMinHeight();
        int maximumY = mode.equals("spawn-biome-height-blocked")
                ? player.getWorld().getMinHeight() : player.getWorld().getMaxHeight();
        yaml.set(root + "height.min", minimumY);
        yaml.set(root + "height.max", maximumY);
        yaml.set(root + "light.min", 0);
        yaml.set(root + "light.max", mode.equals("spawn-biome-light-blocked") ? 0 : 15);
        boolean amountTwo = mode.equals("spawn-biome-limit") || mode.equals("spawn-biome-amount");
        yaml.set(root + "amount.min", amountTwo ? 2 : 1);
        yaml.set(root + "amount.max", amountTwo ? 2 : 1);
        int maximumAlive = mode.equals("spawn-biome-limit") ? 1
                : mode.equals("spawn-biome-amount") ? 2
                : mode.equals("spawn-biome-distance") ? 20 : 3;
        yaml.set(root + "limits.nearby", maximumAlive);
        yaml.set(root + "limits.global", maximumAlive);
        yaml.set(root + "limits.radius", 48);
        yaml.set(root + "level", 1.0);
        yaml.set(root + "despawn-seconds", mode.equals("spawn-biome-despawn") ? 2 : 0);
        save(yaml, file);
    }

    private void writeBossFixture(Player player, String mode) throws IOException {
        writeInventoryRewardGroup();
        Location location = player.getLocation().clone().add(3.0, 0.0, 0.0);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("display.zh_CN", "<!i><red>QA 多阶段 Boss");
        yaml.set("display.en_US", "<!i><red>QA Multi-phase Boss");
        writeBossStages(yaml, mode);
        writeBossLootPolicy(yaml, mode);
        String biomeRoot = "spawning.biome.qa-biome.";
        boolean biomeMode = mode.startsWith("boss-biome");
        yaml.set(biomeRoot + "enabled", biomeMode);
        yaml.set(biomeRoot + "worlds", List.of(mode.equals("boss-biome-world-blocked")
                ? "mtqa_missing_world" : player.getWorld().getName()));
        yaml.set(biomeRoot + "biomes", List.of(mode.equals("boss-biome-biome-blocked")
                ? "NETHER_WASTES" : ((Keyed) player.getLocation().getBlock().getBiome())
                        .getKey().getKey().toUpperCase(Locale.ROOT)));
        yaml.set(biomeRoot + "chance", mode.equals("boss-biome-chance-blocked") ? 0.0 : 1.0);
        yaml.set(biomeRoot + "interval-seconds", 1);
        yaml.set(biomeRoot + "min-distance", 2);
        yaml.set(biomeRoot + "max-distance", 5);
        yaml.set(biomeRoot + "max-active", 1);
        yaml.set(biomeRoot + "minimum-online-players",
                mode.equals("boss-biome-online-blocked") ? 2 : 0);
        String pointRoot = "spawning.points.qa-point.";
        boolean pointMode = mode.equals("boss-point")
                || mode.equals("boss-point-time-window")
                || mode.equals("boss-point-online-blocked");
        yaml.set(pointRoot + "enabled", pointMode);
        yaml.set(pointRoot + "interval-seconds", 1);
        yaml.set(pointRoot + "max-active", 1);
        yaml.set(pointRoot + "minimum-online-players",
                mode.equals("boss-point-online-blocked") ? 2 : 0);
        if (mode.equals("boss-point-time-window")) {
            writeActivePointTimeWindows(yaml, pointRoot);
        }
        setLocation(yaml, pointRoot + "location", location);
        yaml.set("broadcast.spawn.message.zh_CN", "<!i><red>MTQA_BOSS_SPAWN {boss} {location}");
        yaml.set("broadcast.spawn.message.en_US", "<!i><red>MTQA_BOSS_SPAWN {boss} {location}");
        yaml.set("broadcast.spawn.commands", List.of("mttest mark boss-spawn"));
        yaml.set("broadcast.death.message.zh_CN", "<!i><green>MTQA_BOSS_DEATH {boss} {killer}");
        yaml.set("broadcast.death.message.en_US", "<!i><green>MTQA_BOSS_DEATH {boss} {killer}");
        yaml.set("broadcast.death.commands", List.of("mttest mark boss-death"));
        yaml.set("rewards.damage-ranking.enabled", true);
        yaml.set("rewards.damage-ranking.max-recipients", 1);
        yaml.set("rewards.damage-ranking.chat-display", true);
        yaml.set("rewards.damage-ranking.ranks.1", List.of(Map.of("group", "qa-inventory", "copies", 1)));
        yaml.set("rewards.killer.enabled", true);
        yaml.set("rewards.killer.groups", List.of(Map.of("group", "qa-inventory", "copies", 1)));
        if (mode.startsWith("boss-first-")) {
            yaml.set("rewards.first-defeat.enabled", true);
            yaml.set("rewards.first-defeat.scope", mode.equals("boss-first-server") ? "server" : "player");
            yaml.set("rewards.first-defeat.recipient", "killer");
            yaml.set("rewards.first-defeat.entries", List.of(Map.of(
                    "group", "qa-inventory",
                    "entry", "first")));
        }
        save(yaml, new File(target.getDataFolder(), "bosses/qa-boss.yml"));
    }

    private static void writeActivePointTimeWindows(YamlConfiguration yaml, String pointRoot) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
        LocalTime now = LocalTime.now();
        String root = pointRoot + "schedule.";
        yaml.set(root + "timezone", ZoneId.systemDefault().getId());
        yaml.set(root + "fallback-interval-seconds", 3600L);
        yaml.set(root + "time-windows.morning.start", now.minusMinutes(1).format(format));
        yaml.set(root + "time-windows.morning.end", now.plusMinutes(2).format(format));
        yaml.set(root + "time-windows.morning.interval-seconds", 1L);
        yaml.set(root + "time-windows.noon.start", now.plusMinutes(4).format(format));
        yaml.set(root + "time-windows.noon.end", now.plusMinutes(5).format(format));
        yaml.set(root + "time-windows.noon.interval-seconds", 1L);
    }

    private static void writeBossStages(YamlConfiguration yaml, String mode) {
        if (mode.equals("boss-native")) {
            yaml.set("phase-mode", "mythic-native");
            yaml.set("mythic-native.mob", "MTQA_NativeBoss");
            yaml.set("mythic-native.level", 1.0);
            return;
        }
        if (mode.equals("boss-phase-respawn") || mode.startsWith("boss-loot-")
                || mode.startsWith("boss-first-")) {
            yaml.set("phase-mode", "death-respawn");
        }
        if (mode.equals("boss-invalid")) {
            yaml.set("phases", List.of(Map.of("mob", "MTQA_MissingBoss", "level", 1.0)));
            return;
        }
        boolean singleFinalStage = mode.equals("boss-loot-mythictools-only")
                || mode.equals("boss-loot-mythic-only") || mode.equals("boss-loot-combined")
                || mode.startsWith("boss-first-");
        yaml.set("phases", singleFinalStage
                ? List.of(Map.of("mob", "MTQA_BossPhase1", "level", 1.0))
                : List.of(
                        Map.of("mob", "MTQA_BossPhase1", "level", 1.0),
                        Map.of("mob", "MTQA_BossPhase2", "level", 1.0)));
    }

    private static void writeBossLootPolicy(YamlConfiguration yaml, String mode) {
        if (mode.equals("boss-loot-intermediate-none")) {
            yaml.set("loot.intermediate-stage", "none");
            yaml.set("loot.final-stage", "combined");
        } else if (mode.equals("boss-loot-intermediate-mythic")) {
            yaml.set("loot.intermediate-stage", "mythic");
            yaml.set("loot.final-stage", "combined");
        } else if (mode.equals("boss-loot-mythictools-only")) {
            yaml.set("loot.intermediate-stage", "none");
            yaml.set("loot.final-stage", "mythictools-only");
        } else if (mode.equals("boss-loot-mythic-only")) {
            yaml.set("loot.intermediate-stage", "none");
            yaml.set("loot.final-stage", "mythic-only");
        } else if (mode.equals("boss-loot-combined") || mode.equals("boss-native")) {
            yaml.set("loot.intermediate-stage", "none");
            yaml.set("loot.final-stage", "combined");
        }
    }

    private void writeInventoryRewardGroup() throws IOException {
        YamlConfiguration group = new YamlConfiguration();
        group.set("entries.reward.type", "item");
        group.set("entries.reward.material", "EMERALD");
        group.set("entries.reward.weight", 1);
        group.set("entries.reward.min-amount", 2);
        group.set("entries.reward.max-amount", 2);
        group.set("entries.reward.rarity", "epic");
        group.set("entries.reward.delivery", "inventory");
        group.set("entries.reward.display.zh_CN", "<!i><green>QA Boss 奖励");
        group.set("entries.reward.display.en_US", "<!i><green>QA Boss Reward");
        group.set("entries.reward.message.zh_CN", "<!i><green>MTQA_BOSS_REWARD x{amount}");
        group.set("entries.reward.message.en_US", "<!i><green>MTQA_BOSS_REWARD x{amount}");
        group.set("entries.first.type", "item");
        group.set("entries.first.grant-mode", "first-defeat");
        group.set("entries.first.material", "NETHER_STAR");
        group.set("entries.first.min-amount", 1);
        group.set("entries.first.max-amount", 1);
        group.set("entries.first.rarity", "epic");
        group.set("entries.first.delivery", "inventory");
        group.set("entries.first.display.zh_CN", "<!i><light_purple>QA Boss 首次击败奖励");
        group.set("entries.first.display.en_US", "<!i><light_purple>QA Boss First Defeat Reward");
        group.set("entries.first.message.zh_CN", "<!i><green>MTQA_BOSS_FIRST_DEFEAT");
        group.set("entries.first.message.en_US", "<!i><green>MTQA_BOSS_FIRST_DEFEAT");
        save(group, new File(target.getDataFolder(), "drops/groups/qa-inventory.yml"));
    }

    private void reloadAfterMythicMobs(
            CommandSender sender,
            String mode,
            boolean restoring,
            boolean fullRuntimeReload) {
        messages.send(sender, "pending", Map.of("mode", mode));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mythicmobs reload");
        long scheduledGeneration = reloadGeneration;
        pendingReloadTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (scheduledGeneration != reloadGeneration) {
                return;
            }
            pendingReloadTask = null;
            try {
                if (fullRuntimeReload) {
                    target.reloadRuntime();
                } else {
                    target.reloadData();
                }
                messages.send(sender, restoring ? "restore" : "ready", Map.of("mode", mode));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "测试夹具重载失败", exception);
                messages.send(sender, "invalid", Map.of("reason", "reload-" + exception.getClass().getSimpleName()));
            }
        }, 40L);
    }

    private void reloadMythicMobsOnly(CommandSender sender, String mode) {
        messages.send(sender, "pending", Map.of("mode", mode));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mythicmobs reload");
        long scheduledGeneration = reloadGeneration;
        pendingReloadTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (scheduledGeneration != reloadGeneration) {
                        return;
                    }
                    pendingReloadTask = null;
                    if (MythicBukkit.inst().getMobManager().getMythicMob("MTQA_SpawnMob").isPresent()) {
                        messages.send(sender, "ready", Map.of("mode", mode));
                    } else {
                        messages.send(sender, "invalid", Map.of("reason", "mythic-fixture-not-loaded"));
                    }
                },
                40L);
    }

    private void invalidatePendingReload() {
        reloadGeneration++;
        if (pendingReloadTask != null) {
            pendingReloadTask.cancel();
            pendingReloadTask = null;
        }
    }

    private boolean restoreTargetConfig() throws IOException {
        File backup = targetConfigBackup();
        if (!backup.isFile()) {
            return false;
        }
        File config = new File(target.getDataFolder(), "config.yml");
        Files.copy(backup.toPath(), config.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.delete(backup.toPath());
        return true;
    }

    private File targetConfigBackup() {
        return new File(plugin.getDataFolder(), "backups/MythicTools-config.yml");
    }

    private Map<String, java.nio.file.Path> fixtureFiles() {
        Map<String, java.nio.file.Path> files = new LinkedHashMap<>();
        for (String mode : DROP_MODES) {
            String id = "qa-" + mode.substring("drops-".length());
            files.put("drop-group-" + id,
                    new File(target.getDataFolder(), "drops/groups/" + id + ".yml").toPath());
        }
        files.put("drop-group-qa-inventory",
                new File(target.getDataFolder(), "drops/groups/qa-inventory.yml").toPath());
        files.put("drop-mob-MTQA_DropMob",
                new File(target.getDataFolder(), "drops/mobs/MTQA_DropMob.yml").toPath());
        files.put("spawn-point-qa-point",
                new File(target.getDataFolder(), "spawning/points/qa-point.yml").toPath());
        files.put("spawn-biomes",
                new File(target.getDataFolder(), "spawning/biomes.yml").toPath());
        files.put("boss-qa-boss",
                new File(target.getDataFolder(), "bosses/qa-boss.yml").toPath());
        files.put("mythicmob-MTQA", mythicMobFixture().toPath());
        return files;
    }

    private void cleanQaTargetConfigs() throws IOException {
        for (String mode : DROP_MODES) {
            String id = "qa-" + mode.substring("drops-".length());
            Files.deleteIfExists(new File(target.getDataFolder(), "drops/groups/" + id + ".yml").toPath());
        }
        Files.deleteIfExists(new File(target.getDataFolder(), "drops/groups/qa-inventory.yml").toPath());
        Files.deleteIfExists(new File(target.getDataFolder(), "drops/mobs/MTQA_DropMob.yml").toPath());
        Files.deleteIfExists(new File(target.getDataFolder(), "spawning/points/qa-point.yml").toPath());
        Files.deleteIfExists(new File(target.getDataFolder(), "bosses/qa-boss.yml").toPath());
        File biomes = new File(target.getDataFolder(), "spawning/biomes.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(biomes);
        if (yaml.contains("rules.qa-biome")) {
            yaml.set("rules.qa-biome", null);
            save(yaml, biomes);
        }
    }

    private static void setLocation(YamlConfiguration yaml, String path, Location location) {
        yaml.set(path + ".world", location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }

    private static void save(YamlConfiguration yaml, File file) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        yaml.save(file);
    }
}
