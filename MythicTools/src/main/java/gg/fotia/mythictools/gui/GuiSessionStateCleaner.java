package gg.fotia.mythictools.gui;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 统一移除玩家的编辑会话和所有子编辑草稿。 */
final class GuiSessionStateCleaner {
    private GuiSessionStateCleaner() {
    }

    static void cleanup(
            UUID playerId,
            Map<UUID, EditorSession> sessions,
            List<? extends Map<UUID, ?>> draftMaps,
            File dataFolder) throws IOException {
        EditorSession session = sessions.remove(playerId);
        for (Map<UUID, ?> drafts : draftMaps) {
            drafts.remove(playerId);
        }
        if (session != null) {
            CreationDraftCleaner.discard(session, dataFolder);
        }
    }
}
