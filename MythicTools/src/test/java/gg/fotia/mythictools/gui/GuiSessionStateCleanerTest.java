package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.fotia.mythictools.config.YamlFiles;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuiSessionStateCleanerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void clearsEveryDraftAndDeletesUnchangedNewTarget() throws Exception {
        File dataFolder = temporaryDirectory.resolve("data").toFile();
        File draftFile = new File(dataFolder, "bosses/draft.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", false);
        YamlFiles.saveAtomically(yaml, draftFile);
        UUID player = UUID.randomUUID();
        EditorSession session = new EditorSession(AdminType.BOSS, "draft", draftFile, "", yaml);
        session.markNewlyCreated();
        Map<UUID, EditorSession> sessions = new HashMap<>();
        sessions.put(player, session);
        List<Map<UUID, ?>> draftMaps = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            Map<UUID, Object> drafts = new HashMap<>();
            drafts.put(player, new Object());
            draftMaps.add(drafts);
        }

        GuiSessionStateCleaner.cleanup(player, sessions, draftMaps, dataFolder);

        assertFalse(draftFile.exists());
        assertFalse(sessions.containsKey(player));
        assertTrue(draftMaps.stream().noneMatch(map -> map.containsKey(player)));
    }

    @Test
    void clearsDraftsEvenWithoutEditorSession() throws Exception {
        UUID player = UUID.randomUUID();
        Map<UUID, Object> draft = new HashMap<>();
        draft.put(player, new Object());

        GuiSessionStateCleaner.cleanup(
                player, new HashMap<>(), List.of(draft), temporaryDirectory.toFile());

        assertFalse(draft.containsKey(player));
    }
}
