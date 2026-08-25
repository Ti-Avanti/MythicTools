package gg.fotia.mythictools.boss;

import gg.fotia.mythictools.config.ConfigDiagnostic;
import gg.fotia.mythictools.config.ConfigDomain;
import gg.fotia.mythictools.config.ConfigLoadException;
import gg.fotia.mythictools.config.ConfigLoadMode;
import gg.fotia.mythictools.config.ConfigLoadReport;
import gg.fotia.mythictools.config.ConfigProblemCode;
import gg.fotia.mythictools.config.ConfigSeverity;
import gg.fotia.mythictools.config.ConfigValues;
import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.config.PreparedSnapshot;
import gg.fotia.mythictools.config.RepositorySnapshotStore;
import gg.fotia.mythictools.config.RepositoryLoadContext;
import gg.fotia.mythictools.config.YamlFiles;
import gg.fotia.mythictools.integration.MythicMobGateway;
import gg.fotia.mythictools.reward.RewardRepository;
import gg.fotia.mythictools.reward.RewardSnapshot;
import gg.fotia.mythictools.version.BiomeKeys;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** 加载 Boss 阶段、生成器、播报与奖励配置。 */
public final class BossRepository implements BossConfigView {
    private final RepositoryLoadContext context;
    private final PluginSettings settings;
    private final RewardRepository rewards;
    private final RepositorySnapshotStore store;

    public BossRepository(
            JavaPlugin plugin,
            PluginSettings settings,
            MythicMobGateway mythicMobs,
            RewardRepository rewards) {
        this(RepositoryLoadContext.runtime(plugin, mythicMobs), settings, rewards, new RepositorySnapshotStore());
    }

    public BossRepository(
            JavaPlugin plugin,
            PluginSettings settings,
            MythicMobGateway mythicMobs,
            RewardRepository rewards,
            RepositorySnapshotStore store) {
        this(RepositoryLoadContext.runtime(plugin, mythicMobs), settings, rewards, store);
    }

    public BossRepository(
            RepositoryLoadContext context,
            PluginSettings settings,
            RewardRepository rewards,
            RepositorySnapshotStore store) {
        this.context = context;
        this.settings = settings;
        this.rewards = rewards;
        this.store = store;
    }

    /** 兼容旧调用；新运行时应通过 RepositoryReloadCoordinator 一次发布三个仓库。 */
    public void reload() {
        PreparedSnapshot<BossSnapshot> prepared = prepare(ConfigLoadMode.STARTUP_LENIENT);
        if (prepared.report().blocks(ConfigLoadMode.STARTUP_LENIENT)) {
            throw new ConfigLoadException(prepared.report());
        }
        logDiagnostics(prepared.report());
        store.publish(store.current().withBosses(prepared.snapshot()));
    }

    public PreparedSnapshot<BossSnapshot> prepare(ConfigLoadMode mode) {
        return prepare(mode, rewards.currentSnapshot());
    }

    /** 使用同一轮重载生成的奖励候选快照验证 Boss 引用，不读取已发布旧奖励。 */
    public PreparedSnapshot<BossSnapshot> prepare(ConfigLoadMode mode, RewardSnapshot candidateRewards) {
        Map<String, BossConfig> bosses = new LinkedHashMap<>();
        List<ConfigDiagnostic> diagnostics = new ArrayList<>();
        File directory = new File(context.dataFolder(), "bosses");
        File[] files = directory.listFiles((ignored, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return new PreparedSnapshot<>(BossSnapshot.empty(), ConfigLoadReport.empty());
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            try {
                BossConfig boss = parse(file, candidateRewards, diagnostics);
                bosses.put(boss.id(), boss);
            } catch (IOException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.IO_ERROR,
                        "无法读取 Boss 配置", exception));
            } catch (InvalidConfigurationException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.YAML_SYNTAX,
                        "Boss YAML 语法错误", exception));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", problemCode(exception),
                        exception.getMessage(), exception));
            }
        }
        return new PreparedSnapshot<>(new BossSnapshot(bosses), new ConfigLoadReport(diagnostics));
    }

    public Collection<BossConfig> bosses() {
        return store.current().bosses().bosses();
    }

    public BossConfig boss(String id) {
        return store.current().bosses().boss(id);
    }

    public Collection<String> bossIds() {
        return store.current().bosses().bossIds();
    }

    public File bossFile(String id) {
        return new File(context.dataFolder(), "bosses/" + id + ".yml");
    }

    private BossConfig parse(
            File file,
            RewardSnapshot candidateRewards,
            List<ConfigDiagnostic> diagnostics) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = YamlFiles.load(file);
        BossSpawnConfigurationValidator.validate(yaml);
        String id = file.getName().substring(0, file.getName().length() - 4);
        BossPhaseMode phaseMode = BossPhaseMode.fromConfig(yaml.getString("phase-mode", "death-respawn"));
        List<BossPhase> phases = new ArrayList<>();
        if (phaseMode == BossPhaseMode.DEATH_RESPAWN) {
            for (Map<?, ?> raw : yaml.getMapList("phases")) {
                phases.add(parsePhase(raw, file, "阶段"));
            }
            if (phases.isEmpty()) {
                throw new IllegalArgumentException("phases 不能为空");
            }
        }
        BossPhase mythicNativePhase = phaseMode == BossPhaseMode.MYTHIC_NATIVE
                ? parseNativePhase(yaml, file) : null;
        List<BossSpawner> spawners = new ArrayList<>();
        ConfigurationSection spawnerRoot = yaml.getConfigurationSection("spawning");
        if (spawnerRoot != null) {
            ConfigurationSection biomeRoot = spawnerRoot.getConfigurationSection("biome");
            if (biomeRoot != null) {
                for (String spawnerId : biomeRoot.getKeys(false)) {
                    spawners.add(parseBiomeSpawner(spawnerId,
                            requireSection(biomeRoot, spawnerId, file)));
                }
            }
            ConfigurationSection pointRoot = spawnerRoot.getConfigurationSection("points");
            if (pointRoot != null) {
                for (String spawnerId : pointRoot.getKeys(false)) {
                    BossSpawner parsed = parsePointSpawner(spawnerId,
                            requireSection(pointRoot, spawnerId, file), file, diagnostics);
                    if (parsed != null) {
                        spawners.add(parsed);
                    }
                }
            }
        }
        return new BossConfig(
                id,
                readLocalized(yaml, "display", id),
                phaseMode,
                phases,
                mythicNativePhase,
                parseLootPolicy(yaml),
                spawners,
                parseBroadcast(yaml, "broadcast.spawn"),
                parseBroadcast(yaml, "broadcast.death"),
                parseRewards(yaml, file, candidateRewards));
    }

    private BossPhase parseNativePhase(ConfigurationSection yaml, File file) {
        String mobId = requireString(yaml, "mythic-native.mob", file);
        Object level = yaml.get("mythic-native.level");
        return parsePhase(Map.of("mob", mobId, "level", level == null ? 1.0D : level),
                file, "原生阶段");
    }

    private BossPhase parsePhase(Map<?, ?> raw, File file, String label) {
        Object rawMobId = raw.get("mob");
        String mobId = rawMobId == null ? "" : String.valueOf(rawMobId).trim();
        if (mobId.isEmpty() || !context.mythicMobExists().test(mobId)) {
            throw new IllegalArgumentException(file.getName() + " 的" + label
                    + " MythicMob 不存在: " + mobId);
        }
        return new BossPhase(mobId, ConfigValues.doubleValueOrDefault(
                raw.get("level"), 1.0, label + ".level", Double.MIN_VALUE, Double.MAX_VALUE));
    }

    private static BossLootPolicy parseLootPolicy(ConfigurationSection yaml) {
        if (yaml.getConfigurationSection("loot") == null) {
            return BossLootPolicy.legacy();
        }
        return new BossLootPolicy(
                IntermediateStageLootMode.fromConfig(yaml.getString("loot.intermediate-stage", "none")),
                FinalStageLootMode.fromConfig(yaml.getString("loot.final-stage", "mythictools-only")));
    }

    private BossSpawner parseBiomeSpawner(String id, ConfigurationSection section) {
        long intervalSeconds = ConfigValues.longOrDefault(
                section, "interval-seconds", 300L, 1L, Long.MAX_VALUE);
        double spawnChance = ConfigValues.doubleOrDefault(section, "chance", 0.01, 0.0, 1.0);
        int maxActive = ConfigValues.intOrDefault(section, "max-active", 1, 1, Integer.MAX_VALUE);
        int minDistance = ConfigValues.intOrDefault(
                section, "min-distance", settings.defaultMinSpawnDistance(), 1, Integer.MAX_VALUE);
        int maxDistance = ConfigValues.intOrDefault(
                section, "max-distance", settings.defaultMaxSpawnDistance(), 1, Integer.MAX_VALUE);
        ConfigValues.requireOrdered(minDistance, maxDistance, "min-distance", "max-distance");
        Set<Biome> biomes = new LinkedHashSet<>();
        for (String biome : section.getStringList("biomes")) {
            biomes.add(BiomeKeys.parse(biome));
        }
        if (biomes.isEmpty()) {
            throw new IllegalArgumentException("Boss 群系生成器 " + id + " 的 biomes 不能为空");
        }
        return new BossSpawner(
                id,
                BossSpawnType.BIOME,
                section.getBoolean("enabled", true),
                intervalSeconds,
                spawnChance,
                maxActive,
                BossSpawnConfigurationValidator.minimumOnlinePlayers(section),
                null,
                Set.copyOf(section.getStringList("worlds")),
                biomes,
                minDistance,
                maxDistance,
                null);
    }

    private BossSpawner parsePointSpawner(
            String id,
            ConfigurationSection section,
            File file,
            List<ConfigDiagnostic> diagnostics) {
        boolean enabled = section.getBoolean("enabled", true);
        String worldName = requireString(section, "location.world", file);
        World world = context.worldResolver().apply(worldName);
        if (world == null) {
            if (!enabled) {
                diagnostics.add(diagnostic(
                        ConfigSeverity.WARNING, file,
                        "spawning.points." + id + ".location.world",
                        ConfigProblemCode.WORLD_UNAVAILABLE,
                        "未启用的 Boss 固定点世界未加载，已隔离: " + worldName,
                        null));
                return null;
            }
            throw new IllegalArgumentException("Boss 固定点世界未加载: " + worldName);
        }
        Location location = new Location(
                world,
                ConfigValues.doubleOrDefault(
                        section, "location.x", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE),
                ConfigValues.doubleOrDefault(
                        section, "location.y", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE),
                ConfigValues.doubleOrDefault(
                        section, "location.z", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE),
                (float) ConfigValues.doubleOrDefault(
                        section, "location.yaw", 0.0, -Float.MAX_VALUE, Float.MAX_VALUE),
                (float) ConfigValues.doubleOrDefault(
                        section, "location.pitch", 0.0, -Float.MAX_VALUE, Float.MAX_VALUE));
        BossPointSchedule schedule = BossSpawnConfigurationValidator.pointSchedule(section);
        return new BossSpawner(
                id,
                BossSpawnType.POINT,
                enabled,
                schedule.fallbackIntervalSeconds(),
                1.0,
                ConfigValues.intOrDefault(section, "max-active", 1, 1, Integer.MAX_VALUE),
                BossSpawnConfigurationValidator.minimumOnlinePlayers(section),
                schedule,
                Set.of(),
                Set.of(),
                0,
                0,
                location);
    }

    private BossRewardConfig parseRewards(
            ConfigurationSection yaml,
            File file,
            RewardSnapshot candidateRewards) {
        boolean rankingEnabled = yaml.getBoolean("rewards.damage-ranking.enabled", true);
        int maxRecipients = ConfigValues.intOrDefault(
                yaml,
                "rewards.damage-ranking.max-recipients",
                3,
                0,
                settings.safetyLimits().maxBossRecipients());
        Map<Integer, List<GroupReward>> ranks = new HashMap<>();
        ConfigurationSection rankRoot = yaml.getConfigurationSection("rewards.damage-ranking.ranks");
        if (rankRoot != null) {
            for (String rankText : rankRoot.getKeys(false)) {
                int rank;
                try {
                    rank = Integer.parseInt(rankText);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Boss rank 必须是正整数: " + rankText, exception);
                }
                if (rank <= 0) {
                    throw new IllegalArgumentException("Boss rank 必须是正整数: " + rankText);
                }
                List<GroupReward> previous = ranks.put(
                        rank, parseGroupRewards(rankRoot.getMapList(rankText), file, candidateRewards));
                if (previous != null) {
                    throw new IllegalArgumentException("Boss rank 数值重复: " + rankText);
                }
            }
        }
        List<GroupReward> killerRewards = parseGroupRewards(
                yaml.getMapList("rewards.killer.groups"), file, candidateRewards);
        boolean killerEnabled = yaml.getBoolean("rewards.killer.enabled", true);
        if (rankingEnabled && killerEnabled) {
            int killerCopies = totalCopies(killerRewards);
            for (Map.Entry<Integer, List<GroupReward>> entry : ranks.entrySet()) {
                long combined = ConfigValues.addExact(
                        totalCopies(entry.getValue()), killerCopies,
                        "rewards.rank." + entry.getKey() + "+killer.copies");
                if (combined > settings.safetyLimits().maxBossSelectionsPerRecipient()) {
                    throw new IllegalArgumentException(
                            "Boss 单名接收者的排名与击杀奖励总份数不能超过 "
                                    + settings.safetyLimits().maxBossSelectionsPerRecipient());
                }
            }
        }
        return new BossRewardConfig(
                rankingEnabled,
                maxRecipients,
                ranks,
                yaml.getBoolean("rewards.damage-ranking.chat-display", true),
                killerEnabled,
                killerRewards);
    }

    private List<GroupReward> parseGroupRewards(
            List<Map<?, ?>> values,
            File file,
            RewardSnapshot candidateRewards) {
        List<GroupReward> result = new ArrayList<>();
        for (Map<?, ?> raw : values) {
            String group = String.valueOf(raw.get("group"));
            if (candidateRewards.group(group).isEmpty()) {
                throw new IllegalArgumentException(file.getName() + " 引用了不存在的掉落组: " + group);
            }
            int copies = ConfigValues.intValueOrDefault(
                    raw.get("copies"), 1, "rewards.groups.copies", 1,
                    settings.safetyLimits().maxBossGroupCopies());
            result.add(new GroupReward(group, copies));
        }
        totalCopies(result);
        return result;
    }

    private int totalCopies(List<GroupReward> rewards) {
        long total = 0L;
        for (GroupReward reward : rewards) {
            total = ConfigValues.addExact(total, reward.copies(), "rewards.groups.copies");
        }
        if (total > settings.safetyLimits().maxBossSelectionsPerRecipient()) {
            throw new IllegalArgumentException(
                    "Boss 单名接收者的奖励总份数不能超过 "
                            + settings.safetyLimits().maxBossSelectionsPerRecipient());
        }
        return (int) total;
    }

    private static BossBroadcast parseBroadcast(ConfigurationSection yaml, String path) {
        return new BossBroadcast(readLocalized(yaml, path + ".message", ""),
                yaml.getStringList(path + ".commands"));
    }

    private static Map<String, String> readLocalized(ConfigurationSection section, String path, String fallback) {
        ConfigurationSection localized = section.getConfigurationSection(path);
        if (localized == null) {
            String single = section.getString(path, fallback);
            return Map.of("zh_CN", single, "en_US", single);
        }
        Map<String, String> values = new HashMap<>();
        for (String locale : localized.getKeys(false)) {
            values.put(locale, localized.getString(locale, fallback));
        }
        return values;
    }

    private static ConfigurationSection requireSection(ConfigurationSection parent, String path, File file) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException(file.getName() + " 缺少节点 " + path);
        }
        return section;
    }

    private static String requireString(ConfigurationSection section, String path, File file) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(file.getName() + " 缺少字段 " + path);
        }
        return value;
    }

    private ConfigDiagnostic diagnostic(
            ConfigSeverity severity,
            File file,
            String yamlPath,
            ConfigProblemCode code,
            String message,
            Throwable cause) {
        return new ConfigDiagnostic(
                severity, ConfigDomain.BOSSES, file.toPath(), yamlPath, code, message, cause);
    }

    private static ConfigProblemCode problemCode(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("缺少节点")) {
            return ConfigProblemCode.MISSING_SECTION;
        }
        if (message.contains("缺少字段") || message.contains("不能为空")) {
            return ConfigProblemCode.MISSING_VALUE;
        }
        if (message.contains("MythicMob 不存在")) {
            return ConfigProblemCode.UNKNOWN_MYTHIC_MOB;
        }
        if (message.contains("不存在的掉落组")) {
            return ConfigProblemCode.MISSING_REFERENCE;
        }
        if (message.contains("世界未加载")) {
            return ConfigProblemCode.WORLD_UNAVAILABLE;
        }
        return ConfigProblemCode.INVALID_VALUE;
    }

    private void logDiagnostics(ConfigLoadReport report) {
        for (ConfigDiagnostic diagnostic : report.diagnostics()) {
            context.logger().log(
                    diagnostic.severity() == ConfigSeverity.WARNING
                            ? java.util.logging.Level.WARNING : java.util.logging.Level.SEVERE,
                    diagnostic.sourcePath() + "#" + diagnostic.yamlPath() + " ["
                            + diagnostic.problemCode() + "] " + diagnostic.message(),
                    diagnostic.cause());
        }
    }
}
