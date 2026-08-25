package gg.fotia.mythictools.gui;

import java.io.IOException;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** 创建仅存在于编辑会话内存中的 Boss 生成器草稿。 */
final class BossSpawnerDrafts {
    private BossSpawnerDrafts() {
    }

    static EditorSession createBiome(
            EditorSession source,
            String spawnerId,
            String world,
            String biome) throws IOException {
        EditorSession working = detached(source);
        BossSpawnerEditorFields.createBiome(working.yaml, spawnerId, world, biome);
        return working;
    }

    static EditorSession createPoint(
            EditorSession source,
            String spawnerId,
            String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String timezone) throws IOException {
        EditorSession working = detached(source);
        BossSpawnerEditorFields.createPoint(
                working.yaml, spawnerId, world, x, y, z, yaw, pitch, timezone);
        return working;
    }

    private static EditorSession detached(EditorSession source) throws IOException {
        YamlConfiguration copy = new YamlConfiguration();
        try {
            copy.loadFromString(source.yaml.saveToString());
        } catch (InvalidConfigurationException exception) {
            throw new IOException("无法复制 Boss 生成器编辑会话", exception);
        }
        return source.withYaml(copy);
    }
}
