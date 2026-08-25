package gg.fotia.mythictools.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/** 根据编辑会话和分类计算可展示字段。 */
final class EditorFieldCatalog {
    private static final Pattern ENTRY_ITEM_PATH = Pattern.compile("entries\\.[^.]+\\.item(\\..*)?");

    private EditorFieldCatalog() {
    }

    static List<String> fields(EditorSession session, EditorCategory category) {
        ConfigurationSection root = session.rootPath.isEmpty()
                ? session.yaml : session.yaml.getConfigurationSection(session.rootPath);
        if (root == null) {
            return List.of();
        }
        List<String> fields = new ArrayList<>(root.getKeys(true).stream()
                .filter(path -> !root.isConfigurationSection(path))
                .filter(path -> !(root.get(path) instanceof ItemStack))
                .filter(path -> !ENTRY_ITEM_PATH.matcher(path).matches())
                .filter(path -> !path.startsWith("members."))
                .filter(path -> !(session.type == AdminType.MOB_DROP && path.equals("groups")))
                .filter(path -> category == null || category.acceptsField(path))
                .sorted()
                .toList());
        addSpawnSourceFields(session, category, fields);
        addBossFields(session, category, fields);
        return List.copyOf(fields);
    }

    private static void addSpawnSourceFields(
            EditorSession session, EditorCategory category, List<String> fields) {
        if ((session.type != AdminType.BIOME_RULE && session.type != AdminType.SPAWN_POINT)
                || category != null && !category.acceptsField("mob-group")) {
            return;
        }
        ensureField(fields, "mob");
        ensureField(fields, "mob-group");
    }

    private static void addBossFields(
            EditorSession session, EditorCategory category, List<String> fields) {
        if (session.type != AdminType.BOSS) {
            return;
        }
        if (category == null || category == EditorCategory.BOSS_BASIC) {
            List<String> modeFields = BossBasicEditorFields.forMode(session.yaml, fields);
            fields.clear();
            fields.addAll(modeFields);
        }
        if (category == null || category == EditorCategory.BOSS_LOOT) {
            ensureField(fields, "loot.intermediate-stage");
            ensureField(fields, "loot.final-stage");
        }
        if (category != null) {
            List<String> spawnerFields = BossSpawnerEditorFields.withMinimumOnlinePlayers(
                    session.yaml, category, fields);
            fields.clear();
            fields.addAll(spawnerFields);
        }
    }

    private static void ensureField(List<String> fields, String field) {
        if (!fields.contains(field)) {
            fields.add(field);
            fields.sort(String::compareTo);
        }
    }
}
