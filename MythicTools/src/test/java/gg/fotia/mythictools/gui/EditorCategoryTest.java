package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EditorCategoryTest {
    @Test
    void groupsBiomeFieldsIntoUnderstandableSections() {
        assertEquals(List.of("biome-basic", "biome-trigger", "biome-location", "biome-limits"),
                EditorCategory.forType(AdminType.BIOME_RULE).stream().map(EditorCategory::key).toList());
        assertTrue(EditorCategory.BIOME_TRIGGER.acceptsField("worlds"));
        assertTrue(EditorCategory.BIOME_TRIGGER.acceptsField("biomes"));
        assertTrue(EditorCategory.BIOME_LOCATION.acceptsField("height.min"));
        assertTrue(EditorCategory.BIOME_LOCATION.acceptsField("light.max"));
        assertTrue(EditorCategory.BIOME_LIMITS.acceptsField("limits.global"));
        assertTrue(EditorCategory.BIOME_LIMITS.acceptsField("amount.max"));
        assertTrue(EditorCategory.BIOME_BASIC.acceptsField("mob"));
    }

    @Test
    void groupsBossAndMobDropFieldsIntoDedicatedSections() {
        assertEquals(List.of("boss-basic", "boss-loot", "boss-biome-spawning", "boss-point-spawning",
                        "boss-point-schedule", "boss-broadcasts", "boss-ranking-rewards",
                        "boss-killer-rewards", "boss-first-defeat"),
                EditorCategory.forType(AdminType.BOSS).stream().map(EditorCategory::key).toList());
        assertTrue(EditorCategory.BOSS_BASIC.acceptsField("display"));
        assertTrue(EditorCategory.BOSS_BASIC.acceptsField("phase-mode"));
        assertTrue(EditorCategory.BOSS_BASIC.acceptsField("mythic-native.mob"));
        assertTrue(EditorCategory.BOSS_LOOT.acceptsField("loot.final-stage"));
        assertTrue(EditorCategory.BOSS_BIOME_SPAWNING.acceptsField("spawning.biome.forest.chance"));
        assertTrue(EditorCategory.BOSS_POINT_SPAWNING.acceptsField("spawning.points.center.location.x"));
        assertFalse(EditorCategory.BOSS_POINT_SPAWNING
                .acceptsField("spawning.points.center.schedule.time-windows.morning.start"));
        assertFalse(EditorCategory.BOSS_POINT_SPAWNING
                .acceptsField("spawning.points.center.schedule.timezone"));
        assertFalse(EditorCategory.BOSS_POINT_SPAWNING
                .acceptsField("spawning.points.center.schedule.fallback-interval-seconds"));
        assertTrue(EditorCategory.BOSS_BROADCASTS.acceptsField("broadcast.death.message.zh_CN"));
        assertTrue(EditorCategory.BOSS_RANKING_REWARDS.acceptsField("rewards.damage-ranking.max-recipients"));
        assertTrue(EditorCategory.BOSS_KILLER_REWARDS.acceptsField("rewards.killer.groups"));
        assertTrue(EditorCategory.BOSS_FIRST_DEFEAT.acceptsField("rewards.first-defeat.scope"));
        assertEquals(List.of("mob-drop-basic", "mob-drop-groups", "mob-drop-first-defeat"),
                EditorCategory.forType(AdminType.MOB_DROP).stream().map(EditorCategory::key).toList());
        assertTrue(EditorCategory.MOB_DROP_FIRST_DEFEAT.acceptsField("first-defeat.entries"));
    }

    @Test
    void exposesSeparateItemAndCommandRewardCategories() {
        assertEquals(List.of("drop-item-rewards", "drop-command-rewards"),
                EditorCategory.forType(AdminType.DROP_GROUP).stream().map(EditorCategory::key).toList());
        assertTrue(EditorCategory.DROP_ITEM_REWARDS.acceptsRewardType("item"));
        assertTrue(EditorCategory.DROP_COMMAND_REWARDS.acceptsRewardType("command"));
    }
}
