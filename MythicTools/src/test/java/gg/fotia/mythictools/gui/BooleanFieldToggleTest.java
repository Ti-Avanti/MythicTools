package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BooleanFieldToggleTest {

    @Test
    void togglesBothBooleanValuesWithoutTextInput() {
        assertEquals(Boolean.FALSE, BooleanFieldToggle.next(Boolean.TRUE).orElseThrow());
        assertEquals(Boolean.TRUE, BooleanFieldToggle.next(Boolean.FALSE).orElseThrow());
    }

    @Test
    void doesNotTreatOtherFieldTypesAsBooleans() {
        assertTrue(BooleanFieldToggle.next("true").isEmpty());
        assertTrue(BooleanFieldToggle.next(null).isEmpty());
    }

    @Test
    void appliesYamlBooleanToggleWithoutOpeningTextInput() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.points.castle.enabled", false);

        assertTrue(BooleanFieldToggle.apply(yaml, "spawning.points.castle.enabled"));
        assertEquals(true, yaml.getBoolean("spawning.points.castle.enabled"));
    }

    @Test
    void leavesNonBooleanYamlValuesForTheirNormalEditor() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.points.castle.max-active", 1);

        assertTrue(!BooleanFieldToggle.apply(yaml, "spawning.points.castle.max-active"));
        assertEquals(1, yaml.getInt("spawning.points.castle.max-active"));
    }
}
