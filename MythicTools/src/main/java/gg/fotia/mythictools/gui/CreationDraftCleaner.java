package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** 按创建基线清理未保存草稿，同时保护既有文件和共享 YAML 中的其他节点。 */
final class CreationDraftCleaner {
    private CreationDraftCleaner() {
    }

    static void discard(EditorSession session, File dataFolder) throws IOException {
        if (!session.file.isFile()) {
            return;
        }
        File dataRoot = dataFolder.getCanonicalFile();
        File candidate = session.file.getCanonicalFile();
        if (!candidate.toPath().startsWith(dataRoot.toPath())) {
            throw new IOException("拒绝清理插件目录外的配置");
        }
        try {
            YamlConfiguration current = YamlFiles.load(candidate);
            if (!session.isDiscardableNewTarget(current)) {
                return;
            }
            if (session.rootPath.isEmpty()) {
                Files.deleteIfExists(candidate.toPath());
                return;
            }
            current.set(session.rootPath, null);
            if (session.type == AdminType.BIOME_RULE
                    && current.getConfigurationSection("rules") == null) {
                current.createSection("rules");
            }
            YamlFiles.saveAtomically(current, candidate);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("未完成配置格式错误", exception);
        }
    }
}
