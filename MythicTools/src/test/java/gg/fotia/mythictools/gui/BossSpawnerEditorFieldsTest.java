package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BossSpawnerEditorFieldsTest {

    @Test
    void addsMinimumOnlinePlayersForExistingBossSpawners() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.points.castle.enabled", true);
        yaml.set("spawning.biome.forest.enabled", true);

        List<String> pointFields = BossSpawnerEditorFields.withMinimumOnlinePlayers(
                yaml, EditorCategory.BOSS_POINT_SPAWNING, List.of("spawning.points.castle.enabled"));
        List<String> biomeFields = BossSpawnerEditorFields.withMinimumOnlinePlayers(
                yaml, EditorCategory.BOSS_BIOME_SPAWNING, List.of("spawning.biome.forest.enabled"));

        assertTrue(pointFields.contains("spawning.points.castle.minimum-online-players"));
        assertTrue(biomeFields.contains("spawning.biome.forest.minimum-online-players"));
    }

    @Test
    void suppliesZeroAsTheVirtualOnlinePlayerDefault() {
        assertEquals(0, BossSpawnerEditorFields.defaultValue("spawning.points.castle.minimum-online-players"));
    }

    @Test
    void listsSpawnerIdsInStableOrder() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.biome.zeta.enabled", false);
        yaml.set("spawning.biome.alpha.enabled", false);
        yaml.set("spawning.points.castle.enabled", false);

        assertEquals(List.of("alpha", "zeta"),
                BossSpawnerEditorFields.ids(yaml, EditorCategory.BOSS_BIOME_SPAWNING));
        assertEquals(List.of("castle"),
                BossSpawnerEditorFields.ids(yaml, EditorCategory.BOSS_POINT_SPAWNING));
    }

    @Test
    void exposesOnlyFieldsFromTheSelectedSpawner() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.points.castle.enabled", false);
        yaml.set("spawning.points.castle.location.world", "world");
        yaml.set("spawning.points.village.enabled", true);
        yaml.set("spawning.points.village.location.world", "world_nether");

        List<String> fields = BossSpawnerEditorFields.fields(
                yaml, EditorCategory.BOSS_POINT_SPAWNING, "castle");

        assertTrue(fields.contains("spawning.points.castle.enabled"));
        assertTrue(fields.contains("spawning.points.castle.location.world"));
        assertTrue(fields.contains("spawning.points.castle.minimum-online-players"));
        assertFalse(fields.stream().anyMatch(field -> field.contains(".village.")));
    }

    @Test
    void createsDisabledBiomeSpawnerWithStrictRuntimeFields() {
        YamlConfiguration yaml = new YamlConfiguration();

        BossSpawnerEditorFields.createBiome(
                yaml, "forest", "world", "BIRCH_FOREST");

        String root = "spawning.biome.forest.";
        assertFalse(yaml.getBoolean(root + "enabled"));
        assertEquals(List.of("world"), yaml.getStringList(root + "worlds"));
        assertEquals(List.of("BIRCH_FOREST"), yaml.getStringList(root + "biomes"));
        assertEquals(12, yaml.getInt(root + "min-distance"));
        assertEquals(32, yaml.getInt(root + "max-distance"));
        assertEquals(300L, yaml.getLong(root + "interval-seconds"));
        assertEquals(0.01D, yaml.getDouble(root + "chance"));
        assertEquals(1, yaml.getInt(root + "max-active"));
        assertEquals(0, yaml.getInt(root + "minimum-online-players"));
    }

    @Test
    void createsDisabledPointSpawnerAtPlayerLocationWithSchedule() {
        YamlConfiguration yaml = new YamlConfiguration();

        BossSpawnerEditorFields.createPoint(
                yaml, "arena", "world", 10.5, 64.0, -3.25, 90.0F, 12.0F, "Asia/Shanghai");

        String root = "spawning.points.arena.";
        assertFalse(yaml.getBoolean(root + "enabled"));
        assertEquals("world", yaml.getString(root + "location.world"));
        assertEquals(10.5D, yaml.getDouble(root + "location.x"));
        assertEquals(64.0D, yaml.getDouble(root + "location.y"));
        assertEquals(-3.25D, yaml.getDouble(root + "location.z"));
        assertEquals(90.0D, yaml.getDouble(root + "location.yaw"));
        assertEquals(12.0D, yaml.getDouble(root + "location.pitch"));
        assertEquals(1, yaml.getInt(root + "max-active"));
        assertEquals(0, yaml.getInt(root + "minimum-online-players"));
        assertEquals("Asia/Shanghai", yaml.getString(root + "schedule.timezone"));
        assertEquals(7200L, yaml.getLong(root + "schedule.fallback-interval-seconds"));
    }

    @Test
    void rejectsUnsafeOrDuplicateSpawnerIds() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.points.arena.enabled", false);

        assertThrows(IllegalArgumentException.class, () -> BossSpawnerEditorFields.requireCreatableId(
                yaml, EditorCategory.BOSS_POINT_SPAWNING, "../escape"));
        assertThrows(IllegalArgumentException.class, () -> BossSpawnerEditorFields.requireCreatableId(
                yaml, EditorCategory.BOSS_POINT_SPAWNING, "arena"));
        assertEquals("fresh_id", BossSpawnerEditorFields.requireCreatableId(
                yaml, EditorCategory.BOSS_POINT_SPAWNING, " fresh_id "));
    }

    @Test
    void removesOnlyTheSelectedSpawner() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawning.biome.forest.enabled", false);
        yaml.set("spawning.biome.desert.enabled", false);

        BossSpawnerEditorFields.remove(yaml, EditorCategory.BOSS_BIOME_SPAWNING, "forest");

        assertEquals(Set.of("desert"),
                yaml.getConfigurationSection("spawning.biome").getKeys(false));
    }
}
