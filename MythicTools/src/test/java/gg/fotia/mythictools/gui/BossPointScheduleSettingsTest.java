package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.ZoneId;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BossPointScheduleSettingsTest {

    @Test
    void loadsConfiguredValuesAndFallsBackToLegacyPointInterval() {
        YamlConfiguration configured = new YamlConfiguration();
        configured.set("spawning.points.castle.schedule.timezone", "Asia/Shanghai");
        configured.set("spawning.points.castle.schedule.fallback-interval-seconds", 7200L);

        BossPointScheduleSettings settings = BossPointScheduleSettings.load(configured, "castle");

        assertEquals("Asia/Shanghai", settings.timezone());
        assertEquals(7200L, settings.fallbackIntervalSeconds());

        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("spawning.points.castle.interval-seconds", 3600L);
        BossPointScheduleSettings defaults = BossPointScheduleSettings.load(legacy, "castle");

        assertEquals(ZoneId.systemDefault().getId(), defaults.timezone());
        assertEquals(3600L, defaults.fallbackIntervalSeconds());
    }

    @Test
    void validatesAndAppliesEditedSettings() {
        YamlConfiguration yaml = new YamlConfiguration();
        BossPointScheduleSettings settings = BossPointScheduleSettings.load(yaml, "castle")
                .withTimezone("Europe/London")
                .withFallbackInterval("5400");

        settings.applyTo(yaml, "castle");

        assertEquals("Europe/London", yaml.getString("spawning.points.castle.schedule.timezone"));
        assertEquals(5400L,
                yaml.getLong("spawning.points.castle.schedule.fallback-interval-seconds"));
    }

    @Test
    void rejectsInvalidTimezoneAndNonPositiveFallbackInterval() {
        BossPointScheduleSettings settings =
                new BossPointScheduleSettings("Asia/Shanghai", 1800L);

        assertThrows(IllegalArgumentException.class, () -> settings.withTimezone("invalid/timezone"));
        assertThrows(IllegalArgumentException.class, () -> settings.withFallbackInterval("0"));
        assertThrows(IllegalArgumentException.class, () -> settings.withFallbackInterval("not-a-number"));
    }

    @Test
    void scheduleWindowMenuExposesDistinctTimezoneAndFallbackControlsInBothLanguages() {
        YamlConfiguration template = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/gui/boss-time-window-list.yml"));

        assertTrue(template.getStringList("Layout").stream().anyMatch(row -> row.contains("z")));
        assertTrue(template.getStringList("Layout").stream().anyMatch(row -> row.contains("f")));
        assertEquals("COMPASS", template.getString("items.z.material"));
        assertEquals("REPEATER", template.getString("items.f.material"));
        assertNotEquals(template.getString("items.z.material"), template.getString("items.f.material"));

        Set<String> requiredKeys = Set.of(
                "timezone-name", "timezone-lore", "timezone-input", "invalid-timezone",
                "fallback-name", "fallback-lore", "fallback-input", "invalid-fallback");
        for (String locale : Set.of("zh_CN", "en_US")) {
            YamlConfiguration language = YamlConfiguration.loadConfiguration(
                    new File("src/main/resources/lang/" + locale + ".yml"));
            for (String key : requiredKeys) {
                String value = language.getString("gui.boss-time-window-list." + key);
                assertTrue(value != null && value.startsWith("<!i>"), locale + " missing " + key);
            }
        }
    }
}
