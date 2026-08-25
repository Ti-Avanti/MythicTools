package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BossBasicEditorFieldsTest {

    @Test
    void deathRespawnShowsOnlyRespawnPhaseFields() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("phase-mode", "death-respawn");

        assertEquals(List.of("display.zh_CN", "phase-mode", "phases"),
                BossBasicEditorFields.forMode(yaml, List.of(
                        "display.zh_CN", "mythic-native.level", "mythic-native.mob",
                        "phase-mode", "phases")));
    }

    @Test
    void mythicNativeShowsOnlyNativePhaseFields() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("phase-mode", "mythic-native");

        assertEquals(List.of("display.zh_CN", "mythic-native.level", "mythic-native.mob", "phase-mode"),
                BossBasicEditorFields.forMode(yaml, List.of("display.zh_CN", "phase-mode", "phases")));
    }
}
