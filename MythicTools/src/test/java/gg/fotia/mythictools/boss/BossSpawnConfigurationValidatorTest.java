package gg.fotia.mythictools.boss;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BossSpawnConfigurationValidatorTest {

    @Test
    void acceptsNamedWindowsAndIndependentOnlineRequirements() {
        YamlConfiguration yaml = baseConfiguration();
        yaml.set("spawning.points.castle.minimum-online-players", 3);
        yaml.set("spawning.points.castle.schedule.timezone", "Asia/Shanghai");
        yaml.set("spawning.points.castle.schedule.fallback-interval-seconds", 7200L);
        yaml.set("spawning.points.castle.schedule.time-windows.morning.start", "08:00");
        yaml.set("spawning.points.castle.schedule.time-windows.morning.end", "10:00");
        yaml.set("spawning.points.castle.schedule.time-windows.morning.interval-seconds", 1800L);
        yaml.set("spawning.biome.forest.minimum-online-players", 5);

        assertDoesNotThrow(() -> BossSpawnConfigurationValidator.validate(yaml));
    }

    @Test
    void rejectsOverlappingWindowsAndNegativeOnlineRequirement() {
        YamlConfiguration yaml = baseConfiguration();
        yaml.set("spawning.points.castle.minimum-online-players", -1);
        yaml.set("spawning.points.castle.schedule.time-windows.morning.start", "08:00");
        yaml.set("spawning.points.castle.schedule.time-windows.morning.end", "10:00");
        yaml.set("spawning.points.castle.schedule.time-windows.noon.start", "09:00");
        yaml.set("spawning.points.castle.schedule.time-windows.noon.end", "11:00");

        assertThrows(IllegalArgumentException.class, () -> BossSpawnConfigurationValidator.validate(yaml));
    }

    private static YamlConfiguration baseConfiguration() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.points.castle.interval-seconds", 1800L);
        yaml.set("spawning.biome.forest.interval-seconds", 300L);
        return yaml;
    }
}
