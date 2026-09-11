package gg.fotia.mythictools.gui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** 怪物掉落组引用的列表、编辑、掉落组选择与删除确认。 */
final class MobDropGroupGuiController {
    private final GuiContext context;

    MobDropGroupGuiController(GuiContext context) {
        this.context = context;
    }

    void handleMobDropGroupList(Player player, GuiHolder holder, int slot, boolean deleteClick) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || session.type != AdminType.MOB_DROP) {
            context.core.openList(player, AdminType.MOB_DROP, 0);
            return;
        }
        String indexValue = holder.valuesBySlot.get(slot);
        if (indexValue != null) {
            int index = Integer.parseInt(indexValue);
            MobDropGroupDraft draft = MobDropGroupDraft.load(session.yaml, index);
            if (deleteClick) {
                openMobDropGroupConfirm(player, session, index, draft.groupId(), holder.page);
            } else {
                context.sessions.mobDropGroupDrafts.put(player.getUniqueId(), draft);
                openMobDropGroupEditor(player, session, draft);
            }
            return;
        }
        GuiTemplate template = context.screens.template("mob-drop-group-list");
        if (template.slots('p').contains(slot)) {
            openMobDropGroupList(player, session, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openMobDropGroupList(player, session, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            context.sessions.mobDropGroupDrafts.remove(player.getUniqueId());
            context.core.openCategory(player, session);
        } else if (template.slots('c').contains(slot)) {
            openMobDropGroupSelector(player, session, 0);
        }
    }

    void handleMobDropGroupEditor(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        MobDropGroupDraft draft = context.sessions.mobDropGroupDrafts.get(player.getUniqueId());
        if (session == null || session.type != AdminType.MOB_DROP || draft == null) {
            context.core.openList(player, AdminType.MOB_DROP, 0);
            return;
        }
        GuiTemplate template = context.screens.template("mob-drop-group-editor");
        if (template.slots('w').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.mob-drop-group-editor.weight-input"), input -> {
                        try {
                            long value = Long.parseLong(input.trim());
                            GuiWeightRules.requireValid(value, context.safetyLimits);
                            draft.weight(value);
                            context.messages.send(player, "input.accepted", Map.of(
                                    "field", context.messages.text(
                                            player, "gui.mob-drop-group-editor.weight-input"),
                                    "value", value));
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player, "input.invalid",
                                    Map.of("reason", context.messages.text(
                                            player, "gui.mob-drop-group-editor.invalid-weight")
                                            .replace("{max}",
                                                    Long.toString(context.safetyLimits.maxWeight()))));
                        }
                        openMobDropGroupEditor(player, session, draft);
                    }, () -> openMobDropGroupEditor(player, session, draft));
        } else if (template.slots('m').contains(slot)) {
            editMobDropGroupAmount(player, session, draft, true);
        } else if (template.slots('x').contains(slot)) {
            editMobDropGroupAmount(player, session, draft, false);
        } else if (template.slots('b').contains(slot)) {
            context.sessions.mobDropGroupDrafts.remove(player.getUniqueId());
            openMobDropGroupList(player, session, 0);
        } else if (template.slots('s').contains(slot)) {
            saveMobDropGroup(player, session, draft);
        }
    }

    private void editMobDropGroupAmount(
            Player player, EditorSession session, MobDropGroupDraft draft, boolean minimum) {
        String inputKey = minimum
                ? "gui.mob-drop-group-editor.minimum-input"
                : "gui.mob-drop-group-editor.maximum-input";
        context.chatInput.begin(player, context.messages.text(player, inputKey), input -> {
            try {
                int value = Integer.parseInt(input.trim());
                if (value < 0 || value > context.safetyLimits.maxDropsPerMob()
                        || (minimum && value > draft.maximum())
                        || (!minimum && value < draft.minimum())) {
                    throw new IllegalArgumentException("amount");
                }
                if (minimum) {
                    draft.minimum(value);
                } else {
                    draft.maximum(value);
                }
                context.messages.send(player, "input.accepted", Map.of(
                        "field", context.messages.text(player, inputKey), "value", value));
            } catch (IllegalArgumentException exception) {
                context.messages.send(player, "input.invalid", Map.of("reason", context.messages.text(
                        player, "gui.mob-drop-group-editor.invalid-amount")
                        .replace("{max}", Integer.toString(context.safetyLimits.maxDropsPerMob()))));
            }
            openMobDropGroupEditor(player, session, draft);
        }, () -> openMobDropGroupEditor(player, session, draft));
    }

    private void saveMobDropGroup(Player player, EditorSession session, MobDropGroupDraft draft) {
        try {
            draft.validate(context.safetyLimits);
            if (!context.loadedDropGroupIds.get().contains(draft.groupId())) {
                throw new IllegalArgumentException(context.messages.text(
                        player, "gui.mob-drop-group-editor.missing-group")
                        .replace("{id}", draft.groupId()));
            }
        } catch (IllegalArgumentException exception) {
            context.messages.send(player, "input.invalid", Map.of("reason", exception.getMessage()));
            openMobDropGroupEditor(player, session, draft);
            return;
        }
        try {
            EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
            draft.applyTo(working.yaml);
            context.sessionService.persistSession(player, working, () -> {
                context.sessions.mobDropGroupDrafts.remove(player.getUniqueId());
                context.messages.send(player, "common.saved", Map.of());
                context.sessionService.refreshSession(player, session,
                        refreshed -> openMobDropGroupList(player, refreshed, 0));
            }, exception -> {
                context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openMobDropGroupEditor(player, refreshed, draft));
            });
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            context.sessionService.refreshAfterFailure(player, session,
                    refreshed -> openMobDropGroupEditor(player, refreshed, draft));
        }
    }

    void handleMobDropGroupSelector(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || session.type != AdminType.MOB_DROP) {
            context.core.openList(player, AdminType.MOB_DROP, 0);
            return;
        }
        String groupId = holder.valuesBySlot.get(slot);
        if (groupId != null) {
            MobDropGroupDraft draft = MobDropGroupDraft.create(groupId);
            context.sessions.mobDropGroupDrafts.put(player.getUniqueId(), draft);
            openMobDropGroupEditor(player, session, draft);
            return;
        }
        GuiTemplate template = context.screens.template("selector");
        if (template.slots('p').contains(slot)) {
            openMobDropGroupSelector(player, session, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openMobDropGroupSelector(player, session, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            openMobDropGroupList(player, session, 0);
        }
    }

    void handleMobDropGroupConfirm(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || session.type != AdminType.MOB_DROP) {
            context.core.openList(player, AdminType.MOB_DROP, 0);
            return;
        }
        GuiTemplate template = context.screens.template("confirm");
        if (template.slots('y').contains(slot)) {
            try {
                EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
                MobDropGroupDraft.remove(working.yaml, Integer.parseInt(holder.id));
                context.sessionService.persistSession(player, working, () -> {
                    context.messages.send(player, "common.deleted", Map.of("id", holder.context));
                    context.sessionService.refreshSession(player, session,
                            refreshed -> openMobDropGroupList(player, refreshed, holder.page));
                }, exception -> {
                    context.messages.send(player, "common.config-error",
                            Map.of("reason", exception.getMessage()));
                    context.sessionService.refreshAfterFailure(player, session,
                            refreshed -> openMobDropGroupList(player, refreshed, holder.page));
                });
            } catch (IOException | RuntimeException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openMobDropGroupList(player, refreshed, holder.page));
            }
        } else if (template.slots('n').contains(slot)) {
            openMobDropGroupList(player, session, holder.page);
        }
    }

    void openMobDropGroupList(Player player, EditorSession session, int requestedPage) {
        GuiTemplate template = context.screens.template("mob-drop-group-list");
        int entryCount = session.yaml.getMapList("groups").size();
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (entryCount - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(
                GuiView.MOB_DROP_GROUP_LIST, AdminType.MOB_DROP, session.id, page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", session.id, "page", page + 1));
        int start = page * pageSize;
        List<Integer> slots = template.slots('e');
        for (int index = 0; index < slots.size() && start + index < entryCount; index++) {
            int groupIndex = start + index;
            MobDropGroupDraft draft = MobDropGroupDraft.load(session.yaml, groupIndex);
            int slot = slots.get(index);
            inventory.setItem(slot, template.item('e', player, Map.of(
                    "id", draft.groupId(),
                    "weight", draft.weight(),
                    "minimum", draft.minimum(),
                    "maximum", draft.maximum())));
            holder.valuesBySlot.put(slot, Integer.toString(groupIndex));
        }
        for (char symbol : new char[]{'p', 'b', 'c', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    private void openMobDropGroupEditor(
            Player player, EditorSession session, MobDropGroupDraft draft) {
        GuiTemplate template = context.screens.template("mob-drop-group-editor");
        GuiHolder holder = new GuiHolder(
                GuiView.MOB_DROP_GROUP_EDITOR, AdminType.MOB_DROP, draft.groupId(), session.id, 0);
        Map<String, Object> variables = Map.of(
                "id", draft.groupId(),
                "group", draft.groupId(),
                "weight", draft.weight(),
                "minimum", draft.minimum(),
                "maximum", draft.maximum());
        Inventory inventory = context.screens.createInventory(holder, template, player, variables);
        for (char symbol : new char[]{'g', 'w', 'm', 'x', 'b', 's'}) {
            context.screens.fillStatic(inventory, template, symbol, player, variables);
        }
        player.openInventory(inventory);
    }

    private void openMobDropGroupSelector(Player player, EditorSession session, int requestedPage) {
        GuiTemplate template = context.screens.template("selector");
        List<Integer> contentSlots = GuiScreenSupport.selectorContentSlots(template);
        MobDropGroupSelectorModel.Page page = MobDropGroupSelectorModel.page(
                context.loadedDropGroupIds.get(),
                MobDropGroupDraft.groupIds(session.yaml),
                requestedPage,
                contentSlots.size());
        if (page.options().isEmpty()) {
            context.messages.send(player, "gui.mob-drop-group-list.no-available", Map.of());
            openMobDropGroupList(player, session, 0);
            return;
        }
        GuiHolder holder = new GuiHolder(
                GuiView.MOB_DROP_GROUP_SELECTOR, AdminType.MOB_DROP, session.id, page.number());
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "field", context.messages.text(player, "gui.mob-drop-group-editor.selector-field"),
                "page", page.number() + 1));
        for (int index = 0; index < page.options().size(); index++) {
            String groupId = page.options().get(index);
            int slot = contentSlots.get(index);
            ItemStack item = context.screens.selectorItem(
                    template, player, FieldSelectorType.MOB_GROUP, groupId, false);
            item.setType(Material.CHEST);
            inventory.setItem(slot, item);
            holder.valuesBySlot.put(slot, groupId);
        }
        for (char symbol : new char[]{'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    private void openMobDropGroupConfirm(
            Player player, EditorSession session, int index, String groupId, int page) {
        GuiTemplate template = context.screens.template("confirm");
        GuiHolder holder = new GuiHolder(
                GuiView.MOB_DROP_GROUP_CONFIRM,
                AdminType.MOB_DROP,
                Integer.toString(index),
                groupId,
                page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of("id", groupId));
        context.screens.fillStatic(inventory, template, 'y', player, Map.of("id", groupId));
        context.screens.fillStatic(inventory, template, 'n', player, Map.of("id", groupId));
        player.openInventory(inventory);
    }
}
