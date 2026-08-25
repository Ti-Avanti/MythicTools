package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class EditorFieldIconTest {

    @Test
    void mapsRewardFieldsToSemanticIcons() {
        assertEquals(Material.AMETHYST_SHARD, EditorFieldIcon.forField("rarity", "rare"));
        assertEquals(Material.GOLD_NUGGET, EditorFieldIcon.forField("weight", 100));
        assertEquals(Material.LIME_DYE, EditorFieldIcon.forField("min-amount", 1));
        assertEquals(Material.RED_DYE, EditorFieldIcon.forField("max-amount", 2));
        assertEquals(Material.COMMAND_BLOCK, EditorFieldIcon.forField("command", "say hi"));
    }

    @Test
    void mapsCommonSettingsAndUsesPaperAsTheFallback() {
        assertEquals(Material.CLOCK, EditorFieldIcon.forField("interval-seconds", 30));
        assertEquals(Material.OAK_SAPLING, EditorFieldIcon.forField("biomes", java.util.List.of("FOREST")));
        assertEquals(Material.WITHER_SKELETON_SKULL, EditorFieldIcon.forField("phases", java.util.List.of()));
        assertEquals(Material.LIME_DYE, EditorFieldIcon.forField("enabled", true));
        assertEquals(Material.GRAY_DYE, EditorFieldIcon.forField("enabled", false));
        assertEquals(Material.PAPER, EditorFieldIcon.forField("custom-value", "value"));
    }
}
