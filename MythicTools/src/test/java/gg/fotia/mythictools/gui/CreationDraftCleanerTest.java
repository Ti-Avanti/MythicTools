package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.fotia.mythictools.config.YamlFiles;
import java.io.File;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreationDraftCleanerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void deletesOnlyMarkedUnchangedStandaloneDraft() throws Exception {
        File dataFolder = temporaryDirectory.resolve("data").toFile();
        File draftFile = new File(dataFolder, "bosses/draft.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", false);
        YamlFiles.saveAtomically(yaml, draftFile);

        EditorSession draft = new EditorSession(AdminType.BOSS, "draft", draftFile, "", yaml);
        draft.markNewlyCreated();
        CreationDraftCleaner.discard(draft, dataFolder);
        assertFalse(draftFile.exists());

        YamlFiles.saveAtomically(yaml, draftFile);
        EditorSession existing = new EditorSession(AdminType.BOSS, "draft", draftFile, "", yaml);
        CreationDraftCleaner.discard(existing, dataFolder);
        assertTrue(draftFile.isFile());
    }

    @Test
    void preservesDraftAfterItsPersistedValuesChanged() throws Exception {
        File dataFolder = temporaryDirectory.resolve("data").toFile();
        File draftFile = new File(dataFolder, "spawning/points/draft.yml");
        YamlConfiguration baseline = new YamlConfiguration();
        baseline.set("enabled", false);
        YamlFiles.saveAtomically(baseline, draftFile);
        EditorSession draft = new EditorSession(
                AdminType.SPAWN_POINT, "draft", draftFile, "", baseline);
        draft.markNewlyCreated();

        YamlConfiguration saved = new YamlConfiguration();
        saved.set("enabled", true);
        YamlFiles.saveAtomically(saved, draftFile);
        CreationDraftCleaner.discard(draft, dataFolder);

        assertTrue(draftFile.isFile());
        assertTrue(YamlFiles.load(draftFile).getBoolean("enabled"));
    }

    @Test
    void removesOnlyNewBiomeNodeAndPreservesExistingRulesFile() throws Exception {
        File dataFolder = temporaryDirectory.resolve("data").toFile();
        File biomeFile = new File(dataFolder, "spawning/biomes.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("rules.existing.enabled", true);
        yaml.set("rules.draft.enabled", false);
        YamlFiles.saveAtomically(yaml, biomeFile);
        EditorSession draft = new EditorSession(
                AdminType.BIOME_RULE, "draft", biomeFile, "rules.draft", yaml);
        draft.markNewlyCreated();

        CreationDraftCleaner.discard(draft, dataFolder);

        YamlConfiguration remaining = YamlFiles.load(biomeFile);
        assertFalse(remaining.contains("rules.draft"));
        assertTrue(remaining.getBoolean("rules.existing.enabled"));
    }
}
