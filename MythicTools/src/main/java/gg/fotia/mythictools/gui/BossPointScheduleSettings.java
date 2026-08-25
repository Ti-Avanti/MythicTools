package gg.fotia.mythictools.gui;

import java.time.DateTimeException;
import java.time.ZoneId;
import org.bukkit.configuration.file.YamlConfiguration;

/** Boss 固定点时间计划中，由列表页直接维护的全局设置。 */
record BossPointScheduleSettings(String timezone, long fallbackIntervalSeconds) {
    private static final long DEFAULT_INTERVAL_SECONDS = 1800L;

    BossPointScheduleSettings {
        timezone = requireTimezone(timezone);
        if (fallbackIntervalSeconds <= 0L) {
            throw new IllegalArgumentException("fallback interval must be positive");
        }
    }

    static BossPointScheduleSettings load(YamlConfiguration yaml, String pointId) {
        String pointPath = pointPath(pointId);
        long legacyInterval = yaml.getLong(pointPath + ".interval-seconds", DEFAULT_INTERVAL_SECONDS);
        String schedulePath = pointPath + ".schedule";
        String timezone = yaml.getString(schedulePath + ".timezone", ZoneId.systemDefault().getId());
        long fallbackInterval = yaml.getLong(
                schedulePath + ".fallback-interval-seconds", legacyInterval);
        return new BossPointScheduleSettings(timezone, fallbackInterval);
    }

    BossPointScheduleSettings withTimezone(String input) {
        return new BossPointScheduleSettings(requireTimezone(input), fallbackIntervalSeconds);
    }

    BossPointScheduleSettings withFallbackInterval(String input) {
        final long parsed;
        try {
            parsed = Long.parseLong(input.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("fallback interval must be a positive integer", exception);
        }
        return new BossPointScheduleSettings(timezone, parsed);
    }

    void applyTo(YamlConfiguration yaml, String pointId) {
        String schedulePath = pointPath(pointId) + ".schedule";
        yaml.set(schedulePath + ".timezone", timezone);
        yaml.set(schedulePath + ".fallback-interval-seconds", fallbackIntervalSeconds);
    }

    private static String requireTimezone(String input) {
        String candidate = input == null ? "" : input.trim();
        try {
            return ZoneId.of(candidate).getId();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("invalid timezone", exception);
        }
    }

    private static String pointPath(String pointId) {
        return "spawning.points." + pointId;
    }
}
