package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GuiTemplateTest {

    @Test
    void semanticMaterialMustBeExplicitlyEnabledByTheTemplate() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("title-key", "gui.editor.title");
        yaml.set("Layout", List.of("####f####"));
        yaml.set("items.f.material", "PAPER");

        GuiTemplate configuredMaterial = new GuiTemplate("custom", yaml, null, null);
        assertFalse(configuredMaterial.usesSemanticMaterial('f'));

        yaml.set("items.f.semantic-material", true);
        GuiTemplate semanticMaterial = new GuiTemplate("custom", yaml, null, null);
        assertTrue(semanticMaterial.usesSemanticMaterial('f'));
    }

    @Test
    void rejectsTemplateWithoutRequiredContentSlot() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("title-key", "gui.list.title");
        yaml.set("Layout", List.of("#########"));
        yaml.createSection("items");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new GuiTemplate("list", yaml, null, null));

        assertTrue(error.getMessage().contains("必需字符"));
    }

    @Test
    void rejectsDropManagementMenuWithoutBothDestinations() {
        YamlConfiguration yaml = template("#g#####r#", 'g', 'r');

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new GuiTemplate("drops-menu", yaml, null, null));

        assertTrue(error.getMessage().contains("必需字符"));
    }

    @Test
    void rejectsRewardOptionSelectorWithoutEveryFiniteChoice() {
        YamlConfiguration yaml = template("#g#i#c#b#", 'g', 'i', 'c', 'b');

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new GuiTemplate("reward-option-selector", yaml, null, null));

        assertTrue(error.getMessage().contains("必需字符"));
    }

    @Test
    void bundledFiniteChoiceMenusSatisfyTheirTemplateContracts() throws Exception {
        for (String id : List.of("drops-menu", "reward-option-selector")) {
            try (var stream = getClass().getClassLoader().getResourceAsStream("gui/" + id + ".yml")) {
                assertTrue(stream != null, () -> "missing gui resource " + id);
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                new GuiTemplate(id, yaml, null, null);
            }
        }
    }

    @Test
    void everyStructuredEditorRejectsADeletedEssentialSymbol() throws Exception {
        Map<String, Character> essentialSymbols = Map.ofEntries(
                Map.entry("mob-drop-group-list", 'e'),
                Map.entry("mob-drop-group-editor", 's'),
                Map.entry("boss-point-schedule-list", 'e'),
                Map.entry("boss-time-window-list", 'e'),
                Map.entry("boss-time-window-editor", 's'),
                Map.entry("boss-spawner-list", 'e'),
                Map.entry("boss-spawner-editor", 's'));

        for (Map.Entry<String, Character> entry : essentialSymbols.entrySet()) {
            String id = entry.getKey();
            char symbol = entry.getValue();
            try (var stream = getClass().getClassLoader().getResourceAsStream("gui/" + id + ".yml")) {
                assertTrue(stream != null, () -> "missing gui resource " + id);
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                List<String> layout = yaml.getStringList("Layout").stream()
                        .map(line -> line.replace(symbol, '#'))
                        .toList();
                yaml.set("Layout", layout);
                yaml.set("items." + symbol, null);

                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> new GuiTemplate(id, yaml, null, null), id);
                assertTrue(error.getMessage().contains("必需字符"), id + ": " + error.getMessage());
            }
        }
    }

    private static YamlConfiguration template(String layout, char... symbols) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("title-key", "gui.test.title");
        yaml.set("Layout", List.of(layout));
        for (char symbol : symbols) {
            yaml.createSection("items." + symbol);
        }
        return yaml;
    }
}
