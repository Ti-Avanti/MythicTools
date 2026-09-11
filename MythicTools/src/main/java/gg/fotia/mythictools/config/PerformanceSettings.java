package gg.fotia.mythictools.config;

import org.bukkit.configuration.ConfigurationSection;

/** 高频读取和实体生成的可配置工作量上限。 */
public record PerformanceSettings(long placeholderRefreshMillis, int maxSpawnsPerTick) {
    public static PerformanceSettings defaults() {
        return new PerformanceSettings(250L, 32);
    }

    public static PerformanceSettings load(ConfigurationSection config) {
        return new PerformanceSettings(
                ConfigValues.longOrDefault(config, "Performance.Placeholder-Refresh-Millis", 250L, 1L, 60000L),
                ConfigValues.intOrDefault(config, "Spawning.Max-Spawns-Per-Tick", 32, 1, 10000));
    }
}
