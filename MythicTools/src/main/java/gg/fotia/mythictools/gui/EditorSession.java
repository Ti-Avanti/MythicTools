package gg.fotia.mythictools.gui;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** 玩家尚未保存的 YAML 编辑会话。 */
final class EditorSession {
    final AdminType type;
    final String id;
    final File file;
    final String rootPath;
    final YamlConfiguration yaml;
    int page;
    private boolean newlyCreated;
    private final Map<String, Object> baselineValues;

    EditorSession(AdminType type, String id, File file, String rootPath, YamlConfiguration yaml) {
        this.type = type;
        this.id = id;
        this.file = file;
        this.rootPath = rootPath;
        this.yaml = yaml;
        this.baselineValues = valuesAt(yaml, rootPath);
    }

    private EditorSession(EditorSession source, YamlConfiguration yaml) {
        this.type = source.type;
        this.id = source.id;
        this.file = source.file;
        this.rootPath = source.rootPath;
        this.yaml = yaml;
        this.page = source.page;
        this.newlyCreated = source.newlyCreated;
        this.baselineValues = source.baselineValues;
    }

    void markNewlyCreated() {
        newlyCreated = true;
    }

    void inheritCreationDraft(EditorSession source) {
        if (source.newlyCreated) {
            newlyCreated = true;
        }
    }

    boolean isDiscardableEmptyDropGroup() {
        if (!newlyCreated || type != AdminType.DROP_GROUP) {
            return false;
        }
        ConfigurationSection entries = yaml.getConfigurationSection("entries");
        return entries == null || entries.getKeys(false).isEmpty();
    }

    boolean isDiscardableEmptyDropGroup(YamlConfiguration current) {
        if (!isDiscardableEmptyDropGroup() || !isTargetUnchanged(current)) {
            return false;
        }
        ConfigurationSection entries = current.getConfigurationSection("entries");
        return entries == null || entries.getKeys(false).isEmpty();
    }

    boolean isDiscardableNewMobGroup(YamlConfiguration current) {
        return newlyCreated && type == AdminType.MOB_GROUP && isTargetUnchanged(current);
    }

    /** 仅允许清理本次创建且磁盘节点仍与创建基线完全一致的草稿。 */
    boolean isDiscardableNewTarget(YamlConfiguration current) {
        return newlyCreated && isTargetUnchanged(current);
    }

    boolean isTargetUnchanged(YamlConfiguration current) {
        return baselineValues.equals(valuesAt(current, rootPath));
    }

    YamlConfiguration mergeInto(YamlConfiguration latest) {
        if (rootPath.isEmpty()) {
            return yaml;
        }
        latest.set(rootPath, null);
        ConfigurationSection edited = yaml.getConfigurationSection(rootPath);
        if (edited != null) {
            edited.getKeys(true).stream()
                    .filter(key -> !edited.isConfigurationSection(key))
                    .forEach(key -> latest.set(rootPath + "." + key, edited.get(key)));
        }
        return latest;
    }

    EditorSession withYaml(YamlConfiguration replacement) {
        return new EditorSession(this, replacement);
    }

    private static Map<String, Object> valuesAt(YamlConfiguration yaml, String path) {
        ConfigurationSection section = path.isEmpty() ? yaml : yaml.getConfigurationSection(path);
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        section.getKeys(true).stream()
                .filter(key -> !section.isConfigurationSection(key))
                .forEach(key -> values.put(key, section.get(key)));
        return values;
    }
}
