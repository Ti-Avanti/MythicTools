package gg.fotia.mythictools.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GuiLayoutMigrationTest {

    @Test
    void enablesSemanticMaterialOnlyForAnUnmodifiedDefaultItem() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("Layout", List.of("####f####"));
        defaults.set("items.f.material", "PAPER");
        defaults.set("items.f.display-name-key", "gui.editor.field-name");
        defaults.set("items.f.lore-keys", List.of("gui.editor.field-lore"));
        defaults.set("items.f.semantic-material", true);

        YamlConfiguration stock = new YamlConfiguration();
        stock.set("Layout", List.of("####f####"));
        stock.set("items.f.material", "PAPER");
        stock.set("items.f.display-name-key", "gui.editor.field-name");
        stock.set("items.f.lore-keys", List.of("gui.editor.field-lore"));
        assertTrue(GuiLayoutMigration.migrateSemanticMaterial(stock, defaults, 'f'));
        assertTrue(stock.getBoolean("items.f.semantic-material"));

        YamlConfiguration customized = new YamlConfiguration();
        customized.set("Layout", List.of("####f####"));
        customized.set("items.f.material", "DIAMOND_BLOCK");
        customized.set("items.f.display-name-key", "gui.editor.field-name");
        customized.set("items.f.lore-keys", List.of("gui.editor.field-lore"));
        assertFalse(GuiLayoutMigration.migrateSemanticMaterial(customized, defaults, 'f'));
        assertFalse(customized.contains("items.f.semantic-material"));
    }

    @Test
    void expandsThePreviousDefaultCategoryLayoutForNewBossCategories() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Layout", List.of(
                "#########",
                "##g#g#g##",
                "##g#g#g##",
                "####b####"));

        assertTrue(GuiLayoutMigration.migrateCategoryLayout(yaml));
        assertEquals(List.of(
                "#########",
                "##g#g#g##",
                "##g#g#g##",
                "##g#g#g##",
                "####b####"), yaml.getStringList("Layout"));
    }

    @Test
    void upgradesTheKnownDefaultItemRewardTemplateSlotToAChest() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Layout", List.of(
                "#fffffff#",
                "#fffffff#",
                "#fffffff#",
                "####i####",
                "#########",
                "#p#b#s#n#"));
        yaml.set("items.f.material", "NAME_TAG");
        yaml.set("items.i.material", "HOPPER");

        assertTrue(GuiLayoutMigration.migrateItemRewardEditorTemplate(yaml));
        assertEquals("CHEST", yaml.getString("items.i.material"));
    }

    @Test
    void addsScheduleSettingsOnlyToThePreviousDefaultTimeWindowLayout() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Layout", List.of(
                "#eeeeeee#",
                "#eeeeeee#",
                "#eeeeeee#",
                "#eeeeeee#",
                "#########",
                "#p#b#c#n#"));
        yaml.set("items.e.material", "CLOCK");

        assertTrue(GuiLayoutMigration.migrateBossTimeWindowListTemplate(yaml));
        assertEquals("##z###f##", yaml.getStringList("Layout").get(4));
        assertEquals("COMPASS", yaml.getString("items.z.material"));
        assertEquals("REPEATER", yaml.getString("items.f.material"));
        assertEquals("gui.boss-time-window-list.timezone-name",
                yaml.getString("items.z.display-name-key"));
        assertEquals("gui.boss-time-window-list.fallback-name",
                yaml.getString("items.f.display-name-key"));
    }
}
