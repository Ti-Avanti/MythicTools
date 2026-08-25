package gg.fotia.mythictools.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

/** 插件主配置的不可变运行时快照。 */
public record PluginSettings(
        String defaultLocale,
        boolean followClientLocale,
        Map<String, String> localeAliases,
        boolean overrideDrops,
        boolean overrideSpawning,
        boolean overrideBoss,
        long spawnCheckPeriodTicks,
        int spawnLocationAttempts,
        int defaultMinSpawnDistance,
        int defaultMaxSpawnDistance,
        boolean dropOverflowAtFeet,
        String missingImageFallback,
        String databaseFile,
        boolean debug,
        SafetyLimits safetyLimits) {

    public PluginSettings {
        localeAliases = Map.copyOf(localeAliases);
        safetyLimits = java.util.Objects.requireNonNull(safetyLimits, "safetyLimits");
    }

    /** 兼容已有调用；未显式传入时使用文档默认安全上限。 */
    public PluginSettings(
            String defaultLocale,
            boolean followClientLocale,
            Map<String, String> localeAliases,
            boolean overrideDrops,
            boolean overrideSpawning,
            boolean overrideBoss,
            long spawnCheckPeriodTicks,
            int spawnLocationAttempts,
            int defaultMinSpawnDistance,
            int defaultMaxSpawnDistance,
            boolean dropOverflowAtFeet,
            String missingImageFallback,
            String databaseFile,
            boolean debug) {
        this(defaultLocale, followClientLocale, localeAliases, overrideDrops, overrideSpawning, overrideBoss,
                spawnCheckPeriodTicks, spawnLocationAttempts, defaultMinSpawnDistance, defaultMaxSpawnDistance,
                dropOverflowAtFeet, missingImageFallback, databaseFile, debug, SafetyLimits.defaults());
    }

    /** 从 Bukkit 主配置生成经过约束的设置快照。 */
    public static PluginSettings load(FileConfiguration config) {
        Map<String, String> aliases = new LinkedHashMap<>();
        var section = config.getConfigurationSection("Language.Locale-Aliases");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                aliases.put(normalizeLocale(key), section.getString(key, key));
            }
        }
        long checkPeriodTicks = ConfigValues.longOrDefault(
                config, "Spawning.Check-Period-Ticks", 100L, 20L, Long.MAX_VALUE);
        int locationAttempts = ConfigValues.intOrDefault(
                config, "Spawning.Location-Attempts", 12, 1, Integer.MAX_VALUE);
        int minDistance = ConfigValues.intOrDefault(
                config, "Spawning.Default-Min-Distance", 12, 1, Integer.MAX_VALUE);
        int maxDistance = ConfigValues.intOrDefault(
                config, "Spawning.Default-Max-Distance", 32, 1, Integer.MAX_VALUE);
        ConfigValues.requireOrdered(
                minDistance, maxDistance,
                "Spawning.Default-Min-Distance", "Spawning.Default-Max-Distance");
        String configuredDefaultLocale = config.getString("Language.Default", "zh_CN");
        String resolvedDefaultLocale = aliases.getOrDefault(
                normalizeLocale(configuredDefaultLocale), configuredDefaultLocale);
        return new PluginSettings(
                resolvedDefaultLocale,
                config.getBoolean("Language.Follow-Client", true),
                Map.copyOf(aliases),
                config.getBoolean("MythicMobs.Override-Drops", true),
                config.getBoolean("MythicMobs.Override-Spawning", true),
                config.getBoolean("MythicMobs.Override-Boss", true),
                checkPeriodTicks,
                locationAttempts,
                minDistance,
                maxDistance,
                config.getBoolean("Rewards.Drop-Overflow-At-Feet", true),
                config.getString("Text.Missing-Image-Fallback", "<?>"),
                config.getString("Database.File", "data.db"),
                config.getBoolean("Debug", false),
                SafetyLimits.load(config));
    }

    /** 统一客户端语言代码的大小写与分隔符。 */
    public static String normalizeLocale(String locale) {
        return locale == null ? "" : locale.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }
}
