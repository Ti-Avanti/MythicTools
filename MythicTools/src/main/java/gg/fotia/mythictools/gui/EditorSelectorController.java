package gg.fotia.mythictools.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** 通用字段选择器的渲染、翻页与值写入。 */
final class EditorSelectorController {
    private final GuiContext context;

    EditorSelectorController(GuiContext context) {
        this.context = context;
    }

    void openSelector(
            Player player,
            EditorSession session,
            String field,
            FieldSelectorType selectorType,
            EditorCategory category,
            int requestedPage) {
        GuiTemplate template = context.screens.template("selector");
        Set<String> selected = selectedValues(session, field, selectorType);
        LinkedHashSet<String> available = new LinkedHashSet<>(switch (selectorType) {
            case MOB_GROUP, MOB_GROUPS -> context.loadedMobGroupIds.get();
            case MYTHIC_MOB, MYTHIC_MOBS -> context.mobIds.get();
            case LEVEL_POINTS -> context.targets.ids(AdminType.LEVEL_POINT);
            case LEVEL_REGIONS -> context.targets.ids(AdminType.LEVEL_REGION);
            case WORLDGUARD_REGION -> context.plugin instanceof gg.fotia.mythictools.MythicToolsPlugin plugin
                    ? plugin.levelingRepository().worldGuardRegionIds(session.yaml.getString("location.world", ""))
                    : List.of();
            default -> selectorType.options();
        });
        available.addAll(selected);
        List<String> options = List.copyOf(available);
        List<Integer> contentSlots = GuiScreenSupport.selectorContentSlots(template);
        int pageSize = contentSlots.size();
        int maximumPage = Math.max(0, (options.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(GuiView.SELECTOR, session.type, field,
                category == null ? null : category.key(), page);
        String displayField = displayField(category, field);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "field", context.values.fieldLabel(player, displayField),
                "page", page + 1));
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < options.size(); index++) {
            String option = options.get(start + index);
            int slot = contentSlots.get(index);
            inventory.setItem(slot, context.screens.selectorItem(
                    template, player, selectorType, option, selected.contains(option)));
            holder.valuesBySlot.put(slot, option);
        }
        for (char symbol : new char[]{'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleSelector(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || holder.id == null) {
            context.core.openList(player, holder.type, 0);
            return;
        }
        EditorCategory category = holder.context == null
                ? null : EditorCategory.byKey(session.type, holder.context);
        String field = holder.id;
        FieldSelectorType selectorType = FieldSelectorType.fromField(field);
        if (selectorType == null) {
            reopenEditorForField(player, session, category, field);
            return;
        }
        String option = holder.valuesBySlot.get(slot);
        if (option != null) {
            updateSelection(session, field, selectorType, option);
            if (selectorType.multiple()) {
                openSelector(player, session, field, selectorType, category, holder.page);
            } else {
                reopenEditorForField(player, session, category, field);
            }
            return;
        }
        GuiTemplate template = context.screens.template("selector");
        if (template.slots('p').contains(slot)) {
            openSelector(player, session, field, selectorType, category, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openSelector(player, session, field, selectorType, category, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            reopenEditorForField(player, session, category, field);
        }
    }

    private void updateSelection(
            EditorSession session, String field, FieldSelectorType selectorType, String option) {
        String path = EditorValueFormats.absolutePath(session, field);
        if (selectorType.multiple()) {
            Set<String> selected = new LinkedHashSet<>(session.yaml.getStringList(path));
            if (!selected.remove(option)) {
                selected.add(option);
            }
            session.yaml.set(path, new ArrayList<>(selected));
            return;
        }
        session.yaml.set(path, option);
        if (selectorType == FieldSelectorType.MOB_GROUP) {
            session.yaml.set(EditorValueFormats.absolutePath(session, "mob"), null);
        } else if (field.equals("mythic-native.mob") && !session.yaml.isSet("mythic-native.level")) {
            session.yaml.set("mythic-native.level", 1.0);
        }
    }

    private void reopenEditorForField(
            Player player, EditorSession session, EditorCategory category, String field) {
        if (category != null && BossSpawnerGuiController.isBossSpawnerCategory(category)) {
            String spawnerId = BossSpawnerEditorFields.spawnerIdFromField(category, field);
            if (spawnerId != null) {
                context.bossSpawners.openBossSpawnerEditor(player, session, category, spawnerId);
                return;
            }
        }
        context.core.openEditorSession(player, session, category);
    }

    private static Set<String> selectedValues(
            EditorSession session, String field, FieldSelectorType selectorType) {
        String path = EditorValueFormats.absolutePath(session, field);
        if (selectorType.multiple()) {
            return new LinkedHashSet<>(session.yaml.getStringList(path));
        }
        Object current = EditorValueFormats.editorValue(session, field);
        String selected = current == null ? "" : String.valueOf(current);
        return selected.isBlank() ? Set.of() : Set.of(selected);
    }

    private static String displayField(EditorCategory category, String field) {
        if (category == null || !BossSpawnerGuiController.isBossSpawnerCategory(category)) {
            return field;
        }
        String spawnerId = BossSpawnerEditorFields.spawnerIdFromField(category, field);
        return spawnerId == null ? field : BossSpawnerEditorFields.relativeField(category, spawnerId, field);
    }
}
