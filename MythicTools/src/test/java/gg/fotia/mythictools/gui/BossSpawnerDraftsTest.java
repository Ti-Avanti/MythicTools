package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.fotia.mythictools.config.YamlFiles;
import java.io.File;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BossSpawnerDraftsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void pointCreationMutatesOnlyDetachedWorkingSessionUntilSave() throws Exception {
        File file = temporaryDirectory.resolve("boss.yml").toFile();
        YamlConfiguration persisted = new YamlConfiguration();
        persisted.set("mob", "ExampleBoss");
        YamlFiles.saveAtomically(persisted, file);
        EditorSession original = new EditorSession(AdminType.BOSS, "boss", file, "", persisted);

        EditorSession working = BossSpawnerDrafts.createPoint(
                original, "night", "world", 1.0, 64.0, 2.0, 0.0F, 0.0F, "Asia/Shanghai");

        assertTrue(working.yaml.contains("spawning.points.night"));
        assertFalse(original.yaml.contains("spawning.points.night"));
        assertFalse(YamlFiles.load(file).contains("spawning.points.night"));
    }

    @Test
    void detachedDraftPreservesNewTargetCleanupMarker() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", false);
        EditorSession original = new EditorSession(
                AdminType.BOSS, "draft", temporaryDirectory.resolve("draft.yml").toFile(), "", yaml);
        original.markNewlyCreated();

        EditorSession working = BossSpawnerDrafts.createBiome(
                original, "forest", "world", "BIRCH_FOREST");

        assertTrue(working.isDiscardableNewTarget(yaml));
    }
}
