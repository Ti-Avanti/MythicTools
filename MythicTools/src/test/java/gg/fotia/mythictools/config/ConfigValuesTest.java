package gg.fotia.mythictools.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigValuesTest {

    @Test
    void integralReadersUseRawYamlTypesWithoutTruncation() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("int", 7);
        yaml.set("long", 4_000_000_000L);
        yaml.set("fraction", 1.25D);
        yaml.set("whole-double", 2.0D);

        assertEquals(7, ConfigValues.requireInt(yaml, "int", 1, 10));
        assertEquals(4_000_000_000L, ConfigValues.requireLong(yaml, "long", 1, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigValues.requireInt(yaml, "fraction", 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigValues.requireInt(yaml, "whole-double", 0, 10));
    }

    @Test
    void orderedAndExactSumValidationNeverClampOrWrap() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigValues.requireOrdered(3, 2, "min", "max"));
        assertEquals(5L, ConfigValues.addExact(2L, 3L, "weights"));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigValues.addExact(Long.MAX_VALUE, 1L, "weights"));
    }
}
