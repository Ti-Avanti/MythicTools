package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BossTimeWindowDraftTest {

    @Test
    void isolatesAndAppliesNamedTimeWindowChanges() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.points.event.schedule.time-windows.morning.start", "08:00");
        yaml.set("spawning.points.event.schedule.time-windows.morning.end", "10:00");
        yaml.set("spawning.points.event.schedule.time-windows.morning.interval-seconds", 1800L);

        BossTimeWindowDraft draft = BossTimeWindowDraft.load(
                yaml, "spawning.points.event.schedule.time-windows.morning");
        draft.start("09:00");
        draft.end("11:00");
        draft.intervalSeconds(900L);

        assertEquals("08:00", yaml.getString("spawning.points.event.schedule.time-windows.morning.start"));
        draft.applyTo(yaml, "spawning.points.event.schedule.time-windows.morning");
        assertEquals("09:00", yaml.getString("spawning.points.event.schedule.time-windows.morning.start"));
        assertEquals("11:00", yaml.getString("spawning.points.event.schedule.time-windows.morning.end"));
        assertEquals(900L, yaml.getLong("spawning.points.event.schedule.time-windows.morning.interval-seconds"));
    }

    @Test
    void rejectsInvalidTimeAndIntervalValues() {
        BossTimeWindowDraft draft = BossTimeWindowDraft.create();

        assertThrows(IllegalArgumentException.class, () -> draft.start("9am"));
        assertThrows(IllegalArgumentException.class, () -> draft.end("18:60"));
        assertThrows(IllegalArgumentException.class, () -> draft.intervalSeconds(0L));
    }

    @Test
    void rejectsEqualStartAndEndBeforeSaving() {
        BossTimeWindowDraft draft = BossTimeWindowDraft.create();
        draft.end("18:00");

        assertThrows(IllegalArgumentException.class, () -> draft.applyTo(new YamlConfiguration(), "window"));
    }
}
