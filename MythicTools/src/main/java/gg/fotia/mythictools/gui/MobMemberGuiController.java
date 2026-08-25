package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.ConfigValues;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** 怪物组成员的列表、编辑、怪物选择与删除确认。 */
final class MobMemberGuiController {
    private final GuiContext context;

    MobMemberGuiController(GuiContext context) {
        this.context = context;
    }

    void handleMobMemberList(Player player, GuiHolder holder, int slot, boolean deleteClick) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null) {
            context.core.openList(player, AdminType.MOB_GROUP, 0);
            return;
        }
        String memberId = holder.valuesBySlot.get(slot);
        if (memberId != null) {
            if (deleteClick) {
                openMobMemberConfirm(player, session, memberId, holder.page);
            } else {
                MobMemberDraft draft = MobMemberDraft.load(session.yaml, "members." + memberId);
                context.sessions.mobMemberDrafts.put(player.getUniqueId(), draft);
                openMobMemberEditor(player, session, memberId, draft);
            }
            return;
        }
        GuiTemplate template = context.screens.template("mob-member-list");
        if (template.slots('p').contains(slot)) {
            openMobMemberList(player, session, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openMobMemberList(player, session, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            context.core.openCategory(player, session);
        } else if (template.slots('c').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.mob-member-list.input-id"), input -> {
                        String id = input.trim();
                        if (!EditorTargets.SAFE_ID.matcher(id).matches()) {
                            context.messages.send(player, "common.invalid-id", Map.of());
                            openMobMemberList(player, session, 0);
                            return;
                        }
                        if (session.yaml.contains("members." + id)) {
                            context.messages.send(player, "gui.mob-member-list.duplicate-id",
                                    Map.of("id", id));
                            openMobMemberList(player, session, 0);
                            return;
                        }
                        MobMemberDraft draft = MobMemberDraft.create(id);
                        context.sessions.mobMemberDrafts.put(player.getUniqueId(), draft);
                        openMobMemberEditor(player, session, id, draft);
                    }, () -> openMobMemberList(player, session, 0));
        }
    }

    void handleMobMemberEditor(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        MobMemberDraft draft = context.sessions.mobMemberDrafts.get(player.getUniqueId());
        if (session == null || draft == null) {
            context.core.openList(player, AdminType.MOB_GROUP, 0);
            return;
        }
        GuiTemplate template = context.screens.template("mob-member-editor");
        if (template.slots('m').contains(slot)) {
            openMobMemberMobSelector(player, session, holder.id, draft, 0);
        } else if (template.slots('w').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.mob-member-editor.weight-name"), input -> {
                        try {
                            long weight = Long.parseLong(input.trim());
                            GuiWeightRules.requireValid(weight, context.safetyLimits);
                            draft.weight(weight);
                        } catch (IllegalArgumentException exception) {
                            String reason = context.messages.text(
                                    player, "gui.mob-member-editor.invalid-weight")
                                    .replace("{max}", Long.toString(context.safetyLimits.maxWeight()));
                            context.messages.send(player, "input.invalid", Map.of("reason", reason));
                        }
                        openMobMemberEditor(player, session, holder.id, draft);
                    }, () -> openMobMemberEditor(player, session, holder.id, draft));
        } else if (template.slots('b').contains(slot)) {
            context.sessions.mobMemberDrafts.remove(player.getUniqueId());
            openMobMemberList(player, session, 0);
        } else if (template.slots('s').contains(slot)) {
            if (!context.mobExists.test(draft.mobId())) {
                context.messages.send(player, "input.invalid", Map.of("reason", context.messages.text(
                        player, "gui.mob-member-editor.invalid-mob") + ": " + draft.mobId()));
                openMobMemberEditor(player, session, holder.id, draft);
                return;
            }
            try {
                EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
                draft.applyTo(working.yaml, "members." + holder.id);
                context.sessionService.persistSession(player, working);
                context.sessions.mobMemberDrafts.remove(player.getUniqueId());
                context.messages.send(player, "common.saved", Map.of());
                context.sessionService.refreshSession(player, session,
                        refreshed -> openMobMemberList(player, refreshed, 0));
            } catch (IOException | RuntimeException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openMobMemberEditor(player, refreshed, holder.id, draft));
            }
        }
    }

    void handleMobMemberMobSelector(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        MobMemberDraft draft = context.sessions.mobMemberDrafts.get(player.getUniqueId());
        if (session == null || draft == null) {
            context.core.openList(player, AdminType.MOB_GROUP, 0);
            return;
        }
        String mobId = holder.valuesBySlot.get(slot);
        if (mobId != null) {
            draft.mobId(mobId);
            openMobMemberEditor(player, session, holder.id, draft);
            return;
        }
        GuiTemplate template = context.screens.template("selector");
        if (template.slots('p').contains(slot)) {
            openMobMemberMobSelector(player, session, holder.id, draft, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openMobMemberMobSelector(player, session, holder.id, draft, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            openMobMemberEditor(player, session, holder.id, draft);
        }
    }

    void handleMobMemberConfirm(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null) {
            context.core.openList(player, AdminType.MOB_GROUP, 0);
            return;
        }
        GuiTemplate template = context.screens.template("confirm");
        if (template.slots('y').contains(slot)) {
            ConfigurationSection members = session.yaml.getConfigurationSection("members");
            if (members == null || members.getKeys(false).size() <= 1) {
                context.messages.send(player, "gui.mob-member-list.last-member", Map.of());
                openMobMemberList(player, session, holder.page);
                return;
            }
            try {
                EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
                working.yaml.set("members." + holder.id, null);
                context.sessionService.persistSession(player, working);
                context.messages.send(player, "common.deleted", Map.of("id", holder.id));
                context.sessionService.refreshSession(player, session,
                        refreshed -> openMobMemberList(player, refreshed, holder.page));
            } catch (IOException | RuntimeException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openMobMemberList(player, refreshed, holder.page));
            }
        } else if (template.slots('n').contains(slot)) {
            openMobMemberList(player, session, holder.page);
        }
    }

    void openMobMemberList(Player player, EditorSession session, int requestedPage) {
        GuiTemplate template = context.screens.template("mob-member-list");
        ConfigurationSection members = session.yaml.getConfigurationSection("members");
        List<String> ids = members == null ? List.of()
                : members.getKeys(false).stream().sorted().toList();
        long totalWeight = 0L;
        for (String memberId : ids) {
            long weight = ConfigValues.longOrDefault(
                    session.yaml, "members." + memberId + ".weight", 1L, 1L,
                    context.safetyLimits.maxWeight());
            totalWeight = ConfigValues.addExact(totalWeight, weight, "members.weight");
        }
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (ids.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(GuiView.MOB_MEMBER_LIST, AdminType.MOB_GROUP, session.id, page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", session.id, "page", page + 1));
        int start = page * pageSize;
        List<Integer> slots = template.slots('e');
        for (int index = 0; index < slots.size() && start + index < ids.size(); index++) {
            String memberId = ids.get(start + index);
            long weight = ConfigValues.longOrDefault(
                    session.yaml, "members." + memberId + ".weight", 1L, 1L,
                    context.safetyLimits.maxWeight());
            String probability = totalWeight == 0L ? "0.0"
                    : String.format(Locale.ROOT, "%.1f", (double) weight / totalWeight * 100.0);
            int slot = slots.get(index);
            inventory.setItem(slot, template.item('e', player, Map.of(
                    "id", memberId,
                    "mob", session.yaml.getString("members." + memberId + ".mob", ""),
                    "weight", weight,
                    "probability", probability)));
            holder.valuesBySlot.put(slot, memberId);
        }
        for (char symbol : new char[]{'p', 'b', 'c', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    private void openMobMemberEditor(
            Player player, EditorSession session, String memberId, MobMemberDraft draft) {
        GuiTemplate template = context.screens.template("mob-member-editor");
        GuiHolder holder = new GuiHolder(
                GuiView.MOB_MEMBER_EDITOR, AdminType.MOB_GROUP, memberId, session.id, 0);
        Map<String, Object> variables = Map.of(
                "id", memberId, "mob", draft.mobId(), "weight", draft.weight());
        Inventory inventory = context.screens.createInventory(holder, template, player, variables);
        for (char symbol : new char[]{'m', 'w', 'b', 's'}) {
            context.screens.fillStatic(inventory, template, symbol, player, variables);
        }
        player.openInventory(inventory);
    }

    private void openMobMemberMobSelector(
            Player player,
            EditorSession session,
            String memberId,
            MobMemberDraft draft,
            int requestedPage) {
        GuiTemplate template = context.screens.template("selector");
        List<Integer> contentSlots = GuiScreenSupport.selectorContentSlots(template);
        MobMemberMobSelectorModel.Page page = MobMemberMobSelectorModel.page(
                context.mobIds.get(), draft.mobId(), requestedPage, contentSlots.size());
        GuiHolder holder = new GuiHolder(
                GuiView.MOB_MEMBER_MOB_SELECTOR, AdminType.MOB_GROUP, memberId, session.id, page.number());
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "field", context.messages.text(player, "gui.mob-member-editor.mob-name"),
                "page", page.number() + 1));
        for (int index = 0; index < page.options().size(); index++) {
            MobMemberMobSelectorModel.Option option = page.options().get(index);
            int slot = contentSlots.get(index);
            inventory.setItem(slot, context.screens.selectorItem(
                    template, player, FieldSelectorType.MYTHIC_MOB, option.id(), option.selected()));
            holder.valuesBySlot.put(slot, option.id());
        }
        for (char symbol : new char[]{'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    private void openMobMemberConfirm(Player player, EditorSession session, String memberId, int page) {
        GuiTemplate template = context.screens.template("confirm");
        GuiHolder holder = new GuiHolder(
                GuiView.MOB_MEMBER_CONFIRM, AdminType.MOB_GROUP, memberId, session.id, page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of("id", memberId));
        context.screens.fillStatic(inventory, template, 'y', player, Map.of("id", memberId));
        context.screens.fillStatic(inventory, template, 'n', player, Map.of("id", memberId));
        player.openInventory(inventory);
    }
}
