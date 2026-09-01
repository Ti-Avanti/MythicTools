package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class EditorCategoryIconTest {

    @Test
    void mapsEveryEditorCategoryToADistinctSemanticIcon() {
        Map<EditorCategory, Material> expected = Map.ofEntries(
                Map.entry(EditorCategory.BIOME_BASIC, Material.OAK_SAPLING),
                Map.entry(EditorCategory.BIOME_TRIGGER, Material.REDSTONE_TORCH),
                Map.entry(EditorCategory.BIOME_LOCATION, Material.COMPASS),
                Map.entry(EditorCategory.BIOME_LIMITS, Material.IRON_BARS),
                Map.entry(EditorCategory.MOB_DROP_BASIC, Material.ROTTEN_FLESH),
                Map.entry(EditorCategory.MOB_DROP_GROUPS, Material.BARREL),
                Map.entry(EditorCategory.MOB_DROP_FIRST_DEFEAT, Material.TOTEM_OF_UNDYING),
                Map.entry(EditorCategory.DROP_ITEM_REWARDS, Material.DIAMOND),
                Map.entry(EditorCategory.DROP_COMMAND_REWARDS, Material.COMMAND_BLOCK),
                Map.entry(EditorCategory.MOB_GROUP_AMOUNT, Material.DROPPER),
                Map.entry(EditorCategory.MOB_GROUP_MEMBERS, Material.SPAWNER),
                Map.entry(EditorCategory.BOSS_BASIC, Material.NETHER_STAR),
                Map.entry(EditorCategory.BOSS_LOOT, Material.CHEST),
                Map.entry(EditorCategory.BOSS_BIOME_SPAWNING, Material.GRASS_BLOCK),
                Map.entry(EditorCategory.BOSS_POINT_SPAWNING, Material.LODESTONE),
                Map.entry(EditorCategory.BOSS_POINT_SCHEDULE, Material.CLOCK),
                Map.entry(EditorCategory.BOSS_BROADCASTS, Material.BELL),
                Map.entry(EditorCategory.BOSS_RANKING_REWARDS, Material.GOLD_INGOT),
                Map.entry(EditorCategory.BOSS_KILLER_REWARDS, Material.DIAMOND_SWORD),
                Map.entry(EditorCategory.BOSS_FIRST_DEFEAT, Material.DRAGON_EGG));

        assertEquals(EditorCategory.values().length, expected.size());
        expected.forEach((category, material) ->
                assertEquals(material, EditorCategoryIcon.forKey(category.key()), category.key()));
        assertEquals(expected.size(), new HashSet<>(expected.values()).size());
    }

    @Test
    void fallsBackToBookForUnknownOrMissingCategoryKeys() {
        assertEquals(Material.BOOK, EditorCategoryIcon.forKey("future-category"));
        assertEquals(Material.BOOK, EditorCategoryIcon.forKey(null));
    }
}
