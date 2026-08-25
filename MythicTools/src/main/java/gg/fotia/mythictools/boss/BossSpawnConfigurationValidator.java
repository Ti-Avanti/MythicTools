package gg.fotia.mythictools.boss;

import gg.fotia.mythictools.config.ConfigValues;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** 校验 Boss 群系与固定点生成器的共享配置约束。 */
public final class BossSpawnConfigurationValidator {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private BossSpawnConfigurationValidator() {
    }

    public static void validate(YamlConfiguration yaml) {
        validateSpawnerRoot(yaml.getConfigurationSection("spawning.biome"), false);
        validateSpawnerRoot(yaml.getConfigurationSection("spawning.points"), true);
    }

    static BossPointSchedule pointSchedule(ConfigurationSection section) {
        long legacyInterval = ConfigValues.longOrDefault(
                section, "interval-seconds", 1800L, 1L, Long.MAX_VALUE);
        ConfigurationSection root = section.getConfigurationSection("schedule");
        if (root == null) {
            return BossPointSchedule.fixedInterval(legacyInterval);
        }
        long fallbackInterval = ConfigValues.longOrDefault(
                root, "fallback-interval-seconds", legacyInterval, 1L, Long.MAX_VALUE);
        ZoneId timezone = timezone(root.getString("timezone", ZoneId.systemDefault().getId()));
        ConfigurationSection windowsRoot = root.getConfigurationSection("time-windows");
        List<BossTimeWindow> windows = new ArrayList<>();
        if (windowsRoot != null) {
            for (String windowId : windowsRoot.getKeys(false)) {
                ConfigurationSection window = windowsRoot.getConfigurationSection(windowId);
                if (window == null) {
                    throw new IllegalArgumentException("时间段必须是配置节点: " + windowId);
                }
                windows.add(new BossTimeWindow(
                        windowId,
                        time(window.getString("start")),
                        time(window.getString("end")),
                        ConfigValues.longOrDefault(
                                window, "interval-seconds", 1800L, 1L, Long.MAX_VALUE)));
            }
        }
        return new BossPointSchedule(fallbackInterval, timezone, windows);
    }

    static int minimumOnlinePlayers(ConfigurationSection section) {
        return ConfigValues.intOrDefault(
                section, "minimum-online-players", 0, 0, Integer.MAX_VALUE);
    }

    private static void validateSpawnerRoot(ConfigurationSection root, boolean point) {
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                throw new IllegalArgumentException("Boss 生成器必须是配置节点: " + id);
            }
            minimumOnlinePlayers(section);
            if (point) {
                pointSchedule(section);
            } else {
                ConfigValues.longOrDefault(section, "interval-seconds", 300L, 1L, Long.MAX_VALUE);
            }
        }
    }

    private static ZoneId timezone(String value) {
        try {
            return ZoneId.of(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("时区无效: " + value, exception);
        }
    }

    private static LocalTime time(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("时间段缺少开始或结束时间");
        }
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("时间必须是 HH:mm: " + value, exception);
        }
    }
}
