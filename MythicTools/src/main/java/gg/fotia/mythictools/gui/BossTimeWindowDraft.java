package gg.fotia.mythictools.gui;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Boss 固定点时间段编辑器使用的独立草稿，保存前不修改原 YAML。 */
final class BossTimeWindowDraft {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private String start;
    private String end;
    private long intervalSeconds;

    private BossTimeWindowDraft(String start, String end, long intervalSeconds) {
        this.start = validateTime(start);
        this.end = validateTime(end);
        intervalSeconds(intervalSeconds);
    }

    static BossTimeWindowDraft create() {
        return new BossTimeWindowDraft("18:00", "21:00", 1800L);
    }

    static BossTimeWindowDraft load(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("Boss 时间段不存在: " + path);
        }
        return new BossTimeWindowDraft(
                section.getString("start", "18:00"),
                section.getString("end", "21:00"),
                section.getLong("interval-seconds", 1800L));
    }

    String start() {
        return start;
    }

    void start(String value) {
        start = validateTime(value);
    }

    String end() {
        return end;
    }

    void end(String value) {
        end = validateTime(value);
    }

    long intervalSeconds() {
        return intervalSeconds;
    }

    void intervalSeconds(long value) {
        if (value < 1L) {
            throw new IllegalArgumentException("刷新间隔必须至少为 1 秒");
        }
        intervalSeconds = value;
    }

    void applyTo(YamlConfiguration yaml, String path) {
        if (start.equals(end)) {
            throw new IllegalArgumentException("开始时间与结束时间不能相同");
        }
        yaml.set(path, null);
        yaml.set(path + ".start", start);
        yaml.set(path + ".end", end);
        yaml.set(path + ".interval-seconds", intervalSeconds);
    }

    private static String validateTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMAT).format(TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("时间必须是 HH:mm", exception);
        }
    }
}
