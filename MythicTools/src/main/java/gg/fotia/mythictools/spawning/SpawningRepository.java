package gg.fotia.mythictools.spawning;

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
import gg.fotia.mythictools.version.BiomeKeys;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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

/** 加载群系随机生成与固定刷怪点配置。 */
public final class SpawningRepository implements SpawningConfigView {
    private final RepositoryLoadContext context;
    private final PluginSettings settings;
    private final RepositorySnapshotStore store;

    public SpawningRepository(JavaPlugin plugin, PluginSettings settings, MythicMobGateway mythicMobs) {
        this(RepositoryLoadContext.runtime(plugin, mythicMobs), settings, new RepositorySnapshotStore());
    }

    public SpawningRepository(
            JavaPlugin plugin,
            PluginSettings settings,
            MythicMobGateway mythicMobs,
            RepositorySnapshotStore store) {
        this(RepositoryLoadContext.runtime(plugin, mythicMobs), settings, store);
    }

    public SpawningRepository(
            RepositoryLoadContext context,
            PluginSettings settings,
            RepositorySnapshotStore store) {
        this.context = context;
        this.settings = settings;
        this.store = store;
    }

    /** 兼容旧调用；新运行时应通过 RepositoryReloadCoordinator 一次发布三个仓库。 */
    public void reload() {
        PreparedSnapshot<SpawningSnapshot> prepared = prepare(ConfigLoadMode.STARTUP_LENIENT);
        if (prepared.report().blocks(ConfigLoadMode.STARTUP_LENIENT)) {
            throw new ConfigLoadException(prepared.report());
        }
        logDiagnostics(prepared.report());
        store.publish(store.current().withSpawning(prepared.snapshot()));
    }

    /** 只构造本仓库候选快照，不修改当前发布指针。 */
    public PreparedSnapshot<SpawningSnapshot> prepare(ConfigLoadMode mode) {
        Map<String, BiomeSpawnRule> biomeRules = new LinkedHashMap<>();
        Map<String, SpawnPoint> spawnPoints = new LinkedHashMap<>();
        Map<String, MobGroup> mobGroups = new LinkedHashMap<>();
        List<ConfigDiagnostic> diagnostics = new ArrayList<>();
        loadMobGroups(mobGroups, diagnostics);
        loadBiomeRules(mobGroups, biomeRules, diagnostics);
        loadSpawnPoints(mobGroups, spawnPoints, diagnostics);
        return new PreparedSnapshot<>(
                new SpawningSnapshot(biomeRules, spawnPoints, mobGroups),
                new ConfigLoadReport(diagnostics));
    }

    public Collection<BiomeSpawnRule> biomeRules() {
        return store.current().spawning().biomeRules();
    }

    @Override
    public Collection<BiomeSpawnRule> biomeRules(String world, Biome biome) {
        return store.current().spawning().biomeRules(world, biome);
    }

    @Override
    public Object identity() {
        return store.current().spawning();
    }

    public Collection<SpawnPoint> spawnPoints() {
        return store.current().spawning().spawnPoints();
    }

    public Collection<String> biomeRuleIds() {
        return store.current().spawning().biomeRuleIds();
    }

    public Collection<String> spawnPointIds() {
        return store.current().spawning().spawnPointIds();
    }

    public Collection<String> mobGroupIds() {
        return store.current().spawning().mobGroupIds();
    }

    public MobGroup mobGroup(String id) {
        return store.current().spawning().mobGroup(id);
    }

    public SpawnPoint spawnPoint(String id) {
        return store.current().spawning().spawnPoint(id);
    }

    public File biomeFile() {
        return new File(context.dataFolder(), "spawning/biomes.yml");
    }

    public File spawnPointFile(String id) {
        return new File(context.dataFolder(), "spawning/points/" + id + ".yml");
    }

    public File mobGroupFile(String id) {
        return new File(context.dataFolder(), "spawning/groups/" + id + ".yml");
    }

    private void loadMobGroups(Map<String, MobGroup> mobGroups, List<ConfigDiagnostic> diagnostics) {
        File directory = new File(context.dataFolder(), "spawning/groups");
        File[] files = YamlFiles.list(directory);
        if (files == null) {
            return;
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlFiles.load(file);
                String id = file.getName().substring(0, file.getName().length() - 4);
                int minimum = ConfigValues.intOrDefault(
                        yaml, "amount.min", 1, 1, Integer.MAX_VALUE);
                int maximum = ConfigValues.intOrDefault(
                        yaml, "amount.max", minimum, 1, Integer.MAX_VALUE);
                ConfigValues.requireOrdered(minimum, maximum, "amount.min", "amount.max");
                ConfigurationSection members = requireSection(yaml, "members", file);
                List<MobGroupMember> parsed = new ArrayList<>();
                for (String memberId : members.getKeys(false)) {
                    ConfigurationSection member = requireSection(members, memberId, file);
                    String mobId = requireString(member, "mob", file);
                    validateMob(mobId);
                    parsed.add(new MobGroupMember(memberId, mobId,
                            ConfigValues.longOrDefault(member, "weight", 1L, 1L,
                                    settings.safetyLimits().maxWeight())));
                }
                mobGroups.put(id, new MobGroup(id, minimum, maximum, parsed));
            } catch (IOException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.IO_ERROR,
                        "无法读取怪物组配置", exception));
            } catch (InvalidConfigurationException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.YAML_SYNTAX,
                        "怪物组 YAML 语法错误", exception));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "members", problemCode(exception),
                        exception.getMessage(), exception));
            }
        }
    }

    private void loadBiomeRules(
            Map<String, MobGroup> mobGroups,
            Map<String, BiomeSpawnRule> biomeRules,
            List<ConfigDiagnostic> diagnostics) {
        File file = biomeFile();
        try {
            YamlConfiguration yaml = YamlFiles.load(file);
            ConfigurationSection rules = requireSection(yaml, "rules", file);
            for (String id : rules.getKeys(false)) {
                try {
                    ConfigurationSection section = requireSection(rules, id, file);
                    BiomeNumbers numbers = parseBiomeNumbers(section);
                    SpawnSource source = source(section, file, mobGroups);
                    Set<Biome> biomes = new LinkedHashSet<>();
                    for (String value : section.getStringList("biomes")) {
                        biomes.add(BiomeKeys.parse(value));
                    }
                    if (biomes.isEmpty()) {
                        throw new IllegalArgumentException("rules." + id + ".biomes 不能为空");
                    }
                    biomeRules.put(id, new BiomeSpawnRule(
                            id,
                            source.mobId,
                            source.mobGroupId,
                            Set.copyOf(section.getStringList("worlds")),
                            biomes,
                            numbers.chance,
                            numbers.intervalSeconds,
                            numbers.minDistance,
                            numbers.maxDistance,
                            numbers.minY,
                            numbers.maxY,
                            numbers.minLight,
                            numbers.maxLight,
                            numbers.minAmount,
                            numbers.maxAmount,
                            numbers.maxAliveNearby,
                            numbers.maxAliveGlobal,
                            numbers.nearbyRadius,
                            numbers.level,
                            numbers.despawnSeconds,
                            section.getBoolean("enabled", true)));
                } catch (IllegalArgumentException exception) {
                    diagnostics.add(diagnostic(
                            ConfigSeverity.ERROR, file, "rules." + id, problemCode(exception),
                            exception.getMessage(), exception));
                }
            }
        } catch (IOException exception) {
            diagnostics.add(diagnostic(ConfigSeverity.FATAL, file, "rules", ConfigProblemCode.IO_ERROR,
                    "无法读取群系刷怪根配置", exception));
        } catch (InvalidConfigurationException exception) {
            diagnostics.add(diagnostic(ConfigSeverity.FATAL, file, "$", ConfigProblemCode.YAML_SYNTAX,
                    "群系刷怪 YAML 语法错误", exception));
        } catch (IllegalArgumentException exception) {
            diagnostics.add(diagnostic(ConfigSeverity.FATAL, file, "rules", problemCode(exception),
                    exception.getMessage(), exception));
        }
    }

    private BiomeNumbers parseBiomeNumbers(ConfigurationSection section) {
        double chance = ConfigValues.doubleOrDefault(section, "chance", 0.1, 0.0, 1.0);
        long intervalSeconds = ConfigValues.longOrDefault(
                section, "interval-seconds", 30L, 1L, Long.MAX_VALUE);
        int minDistance = ConfigValues.intOrDefault(
                section, "min-distance", settings.defaultMinSpawnDistance(), 1, Integer.MAX_VALUE);
        int maxDistance = ConfigValues.intOrDefault(
                section, "max-distance", settings.defaultMaxSpawnDistance(), 1, Integer.MAX_VALUE);
        ConfigValues.requireOrdered(minDistance, maxDistance, "min-distance", "max-distance");
        int minY = ConfigValues.intOrDefault(
                section, "height.min", -64, Integer.MIN_VALUE, Integer.MAX_VALUE);
        int maxY = ConfigValues.intOrDefault(
                section, "height.max", 320, Integer.MIN_VALUE, Integer.MAX_VALUE);
        ConfigValues.requireOrdered(minY, maxY, "height.min", "height.max");
        int minLight = ConfigValues.intOrDefault(section, "light.min", 0, 0, 15);
        int maxLight = ConfigValues.intOrDefault(section, "light.max", 15, 0, 15);
        ConfigValues.requireOrdered(minLight, maxLight, "light.min", "light.max");
        int minAmount = ConfigValues.intOrDefault(section, "amount.min", 1, 1, Integer.MAX_VALUE);
        int maxAmount = ConfigValues.intOrDefault(
                section, "amount.max", minAmount, 1, Integer.MAX_VALUE);
        ConfigValues.requireOrdered(minAmount, maxAmount, "amount.min", "amount.max");
        int maxAliveNearby = ConfigValues.intOrDefault(
                section, "limits.nearby", 3, 1, Integer.MAX_VALUE);
        int maxAliveGlobal = ConfigValues.intOrDefault(
                section, "limits.global", 20, 1, Integer.MAX_VALUE);
        int nearbyRadius = ConfigValues.intOrDefault(
                section, "limits.radius", 48, 1, Integer.MAX_VALUE);
        double level = ConfigValues.doubleOrDefault(
                section, "level", 1.0, Double.MIN_VALUE, Double.MAX_VALUE);
        long despawnSeconds = ConfigValues.longOrDefault(
                section, "despawn-seconds", 0L, 0L, Long.MAX_VALUE);
        return new BiomeNumbers(
                chance, intervalSeconds, minDistance, maxDistance, minY, maxY,
                minLight, maxLight, minAmount, maxAmount, maxAliveNearby,
                maxAliveGlobal, nearbyRadius, level, despawnSeconds);
    }

    private void loadSpawnPoints(
            Map<String, MobGroup> mobGroups,
            Map<String, SpawnPoint> spawnPoints,
            List<ConfigDiagnostic> diagnostics) {
        File directory = new File(context.dataFolder(), "spawning/points");
        File[] files = YamlFiles.list(directory);
        if (files == null) {
            return;
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlFiles.load(file);
                String id = file.getName().substring(0, file.getName().length() - 4);
                SpawnSource source = source(yaml, file, mobGroups);
                boolean enabled = yaml.getBoolean("enabled", true);
                String worldName = requireString(yaml, "location.world", file);
                World world = context.worldResolver().apply(worldName);
                if (world == null) {
                    if (!enabled) {
                        diagnostics.add(diagnostic(
                                ConfigSeverity.WARNING, file, "location.world",
                                ConfigProblemCode.WORLD_UNAVAILABLE,
                                "未启用的刷怪点世界未加载，已隔离: " + worldName, null));
                        continue;
                    }
                    throw new IllegalArgumentException("世界未加载: " + worldName);
                }
                Location location = new Location(
                        world,
                        ConfigValues.doubleOrDefault(
                                yaml, "location.x", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE),
                        ConfigValues.doubleOrDefault(
                                yaml, "location.y", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE),
                        ConfigValues.doubleOrDefault(
                                yaml, "location.z", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE),
                        (float) ConfigValues.doubleOrDefault(
                                yaml, "location.yaw", 0.0, -Float.MAX_VALUE, Float.MAX_VALUE),
                        (float) ConfigValues.doubleOrDefault(
                                yaml, "location.pitch", 0.0, -Float.MAX_VALUE, Float.MAX_VALUE));
                int minAmount = ConfigValues.intOrDefault(
                        yaml, "amount.min", 1, 1, Integer.MAX_VALUE);
                int maxAmount = ConfigValues.intOrDefault(
                        yaml, "amount.max", minAmount, 1, Integer.MAX_VALUE);
                ConfigValues.requireOrdered(minAmount, maxAmount, "amount.min", "amount.max");
                spawnPoints.put(id, new SpawnPoint(
                        id,
                        source.mobId,
                        source.mobGroupId,
                        location,
                        ConfigValues.longOrDefault(
                                yaml, "interval-seconds", 60L, 1L, Long.MAX_VALUE),
                        minAmount,
                        maxAmount,
                        ConfigValues.intOrDefault(yaml, "max-alive", 1, 1, Integer.MAX_VALUE),
                        ConfigValues.doubleOrDefault(
                                yaml, "level", 1.0, Double.MIN_VALUE, Double.MAX_VALUE),
                        yaml.getBoolean("spawn-on-death", false),
                        ConfigValues.longOrDefault(
                                yaml, "despawn-seconds", 0L, 0L, Long.MAX_VALUE),
                        enabled));
            } catch (IOException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.IO_ERROR,
                        "无法读取刷怪点配置", exception));
            } catch (InvalidConfigurationException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.YAML_SYNTAX,
                        "刷怪点 YAML 语法错误", exception));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", problemCode(exception),
                        exception.getMessage(), exception));
            }
        }
    }

    private void validateMob(String mobId) {
        if (!context.mythicMobExists().test(mobId)) {
            throw new IllegalArgumentException("MythicMob 不存在: " + mobId);
        }
    }

    private SpawnSource source(
            ConfigurationSection section,
            File file,
            Map<String, MobGroup> mobGroups) {
        String mobId = section.getString("mob");
        String groupId = section.getString("mob-group");
        boolean hasMob = mobId != null && !mobId.isBlank();
        boolean hasGroup = groupId != null && !groupId.isBlank();
        if (hasMob == hasGroup) {
            throw new IllegalArgumentException(file.getName() + " 必须且只能配置 mob 或 mob-group");
        }
        if (hasMob) {
            validateMob(mobId);
            return new SpawnSource(mobId, null);
        }
        if (!mobGroups.containsKey(groupId)) {
            throw new IllegalArgumentException(file.getName() + " 引用了不存在的怪物组: " + groupId);
        }
        return new SpawnSource(null, groupId);
    }

    private record SpawnSource(String mobId, String mobGroupId) {
    }

    private record BiomeNumbers(
            double chance,
            long intervalSeconds,
            int minDistance,
            int maxDistance,
            int minY,
            int maxY,
            int minLight,
            int maxLight,
            int minAmount,
            int maxAmount,
            int maxAliveNearby,
            int maxAliveGlobal,
            int nearbyRadius,
            double level,
            long despawnSeconds) {
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
                severity, ConfigDomain.SPAWNING, file.toPath(), yamlPath, code, message, cause);
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
        if (message.contains("不存在的怪物组")) {
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
