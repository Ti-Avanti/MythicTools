package gg.fotia.mythictools.gui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Boss 阶段的列表、编辑、怪物选择、排序与删除确认。 */
final class BossPhaseGuiController {
    private final GuiContext context;

    BossPhaseGuiController(GuiContext context) {
        this.context = context;
    }

    void openBossPhaseList(Player player, EditorSession session, int requestedPage) {
        GuiTemplate template = context.screens.template("boss-phase-list");
        List<Map<?, ?>> phases = session.yaml.getMapList("phases");
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (phases.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_PHASE_LIST, AdminType.BOSS, session.id, EditorCategory.BOSS_BASIC.key(), page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("boss", session.id, "page", page + 1));
        int start = page * pageSize;
        List<Integer> slots = template.slots('e');
        for (int offset = 0; offset < slots.size() && start + offset < phases.size(); offset++) {
            int index = start + offset;
            Map<?, ?> phase = phases.get(index);
            Object rawMob = phase.get("mob");
            int slot = slots.get(offset);
            inventory.setItem(slot, template.item('e', player, Map.of(
                    "number", index + 1,
                    "mob", rawMob == null ? "" : String.valueOf(rawMob),
                    "level", EditorValueFormats.formatValue(phase.get("level")))));
            holder.valuesBySlot.put(slot, Integer.toString(index));
        }
        for (char symbol : new char[]{'p', 'b', 'c', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleBossPhaseList(Player player, GuiHolder holder, int slot, boolean deleteClick) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        String rawIndex = holder.valuesBySlot.get(slot);
        if (rawIndex != null) {
            int index = Integer.parseInt(rawIndex);
            if (deleteClick) {
                openBossPhaseConfirm(player, session, index, holder.page);
            } else {
                BossPhaseDraft draft = BossPhaseDraft.load(session.yaml, index);
                context.sessions.bossPhaseDrafts.put(player.getUniqueId(), draft);
                openBossPhaseEditor(player, session, index, draft);
            }
            return;
        }
        GuiTemplate template = context.screens.template("boss-phase-list");
        if (template.slots('p').contains(slot)) {
            openBossPhaseList(player, session, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openBossPhaseList(player, session, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            context.sessions.bossPhaseDrafts.remove(player.getUniqueId());
            session.page = 0;
            context.core.openEditorSession(player, session, EditorCategory.BOSS_BASIC);
        } else if (template.slots('c').contains(slot)) {
            String defaultMob = context.mobIds.get().stream()
                    .filter(id -> id != null && !id.isBlank() && context.mobExists.test(id))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .findFirst()
                    .orElse(null);
            if (defaultMob == null) {
                context.messages.send(player, "gui.boss-phase-list.no-loaded-mobs", Map.of());
                openBossPhaseList(player, session, holder.page);
                return;
            }
            int index = BossPhaseDraft.count(session.yaml);
            BossPhaseDraft draft = BossPhaseDraft.create(defaultMob);
            context.sessions.bossPhaseDrafts.put(player.getUniqueId(), draft);
            openBossPhaseEditor(player, session, index, draft);
        }
    }

    private void openBossPhaseEditor(
            Player player, EditorSession session, int index, BossPhaseDraft draft) {
        GuiTemplate template = context.screens.template("boss-phase-editor");
        GuiHolder holder = new GuiHolder(GuiView.BOSS_PHASE_EDITOR, AdminType.BOSS,
                Integer.toString(index), EditorCategory.BOSS_BASIC.key(), 0);
        Map<String, Object> variables = Map.of(
                "number", index + 1, "mob", draft.mobId(),
                "level", EditorValueFormats.formatValue(draft.level()));
        Inventory inventory = context.screens.createInventory(holder, template, player, variables);
        for (char symbol : new char[]{'m', 'l', 'u', 'd', 'b', 's'}) {
            context.screens.fillStatic(inventory, template, symbol, player, variables);
        }
        player.openInventory(inventory);
    }

    void handleBossPhaseEditor(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        BossPhaseDraft draft = context.sessions.bossPhaseDrafts.get(player.getUniqueId());
        if (session == null || draft == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        int index = Integer.parseInt(holder.id);
        GuiTemplate template = context.screens.template("boss-phase-editor");
        if (template.slots('m').contains(slot)) {
            openBossPhaseMobSelector(player, session, index, draft, 0);
        } else if (template.slots('l').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.boss-phase-editor.level-input"), input -> {
                        try {
                            draft.level(Double.parseDouble(input.trim()));
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player, "gui.boss-phase-editor.invalid-level", Map.of());
                        }
                        openBossPhaseEditor(player, session, index, draft);
                    }, () -> openBossPhaseEditor(player, session, index, draft));
        } else if (template.slots('u').contains(slot)) {
            moveBossPhase(player, session, index, draft, -1);
        } else if (template.slots('d').contains(slot)) {
            moveBossPhase(player, session, index, draft, 1);
        } else if (template.slots('b').contains(slot)) {
            context.sessions.bossPhaseDrafts.remove(player.getUniqueId());
            int pageSize = context.screens.template("boss-phase-list").slots('e').size();
            openBossPhaseList(player, session, Math.max(0, index / pageSize));
        } else if (template.slots('s').contains(slot)) {
            saveBossPhase(player, session, index, draft);
        }
    }

    private void openBossPhaseMobSelector(
            Player player, EditorSession session, int index, BossPhaseDraft draft, int requestedPage) {
        GuiTemplate template = context.screens.template("selector");
        List<Integer> slots = GuiScreenSupport.selectorContentSlots(template);
        MobMemberMobSelectorModel.Page page = MobMemberMobSelectorModel.page(
                context.mobIds.get(), draft.mobId(), requestedPage, slots.size());
        GuiHolder holder = new GuiHolder(GuiView.BOSS_PHASE_MOB_SELECTOR, AdminType.BOSS,
                Integer.toString(index), EditorCategory.BOSS_BASIC.key(), page.number());
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "field", context.messages.text(player, "gui.boss-phase-editor.mob-name"),
                "page", page.number() + 1));
        for (int offset = 0; offset < page.options().size(); offset++) {
            MobMemberMobSelectorModel.Option option = page.options().get(offset);
            int slot = slots.get(offset);
            inventory.setItem(slot, context.screens.selectorItem(
                    template, player, FieldSelectorType.MYTHIC_MOB, option.id(), option.selected()));
            holder.valuesBySlot.put(slot, option.id());
        }
        for (char symbol : new char[]{'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleBossPhaseMobSelector(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        BossPhaseDraft draft = context.sessions.bossPhaseDrafts.get(player.getUniqueId());
        if (session == null || draft == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        int index = Integer.parseInt(holder.id);
        String mobId = holder.valuesBySlot.get(slot);
        if (mobId != null) {
            draft.mobId(mobId);
            openBossPhaseEditor(player, session, index, draft);
            return;
        }
        GuiTemplate template = context.screens.template("selector");
        if (template.slots('p').contains(slot)) {
            openBossPhaseMobSelector(player, session, index, draft, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openBossPhaseMobSelector(player, session, index, draft, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            openBossPhaseEditor(player, session, index, draft);
        }
    }

    private void saveBossPhase(Player player, EditorSession session, int index, BossPhaseDraft draft) {
        if (!context.mobExists.test(draft.mobId())) {
            context.messages.send(player, "input.invalid", Map.of("reason",
                    context.messages.text(player, "gui.boss.invalid-mob") + ": " + draft.mobId()));
            openBossPhaseEditor(player, session, index, draft);
            return;
        }
        try {
            EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
            int savedIndex = draft.applyTo(working.yaml, index);
            context.sessionService.persistSession(player, working, () -> {
                context.sessions.bossPhaseDrafts.remove(player.getUniqueId());
                context.messages.send(player, "common.saved", Map.of());
                int pageSize = context.screens.template("boss-phase-list").slots('e').size();
                int returnPage = Math.max(0, savedIndex / pageSize);
                context.sessionService.refreshSession(player, session,
                        refreshed -> openBossPhaseList(player, refreshed, returnPage));
            }, exception -> {
                context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openBossPhaseEditor(player, refreshed, index, draft));
            });
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            context.sessionService.refreshAfterFailure(player, session,
                    refreshed -> openBossPhaseEditor(player, refreshed, index, draft));
        }
    }

    private void moveBossPhase(
            Player player, EditorSession session, int index, BossPhaseDraft draft, int offset) {
        if (!context.mobExists.test(draft.mobId())) {
            context.messages.send(player, "input.invalid", Map.of("reason",
                    context.messages.text(player, "gui.boss.invalid-mob") + ": " + draft.mobId()));
            openBossPhaseEditor(player, session, index, draft);
            return;
        }
        int target = index + offset;
        if (target < 0 || target >= BossPhaseDraft.count(session.yaml)) {
            context.messages.send(player, "gui.boss-phase-editor.move-boundary", Map.of());
            openBossPhaseEditor(player, session, index, draft);
            return;
        }
        try {
            EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
            int resolvedIndex = draft.applyTo(working.yaml, index);
            int movedIndex = BossPhaseDraft.move(working.yaml, resolvedIndex, offset);
            context.sessionService.persistSession(player, working, () -> {
                context.messages.send(player, "common.saved", Map.of());
                context.sessionService.refreshSession(player, session, refreshed -> {
                    BossPhaseDraft moved = BossPhaseDraft.load(refreshed.yaml, movedIndex);
                    context.sessions.bossPhaseDrafts.put(player.getUniqueId(), moved);
                    openBossPhaseEditor(player, refreshed, movedIndex, moved);
                });
            }, exception -> {
                context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openBossPhaseEditor(player, refreshed, index, draft));
            });
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            context.sessionService.refreshAfterFailure(player, session,
                    refreshed -> openBossPhaseEditor(player, refreshed, index, draft));
        }
    }

    private void openBossPhaseConfirm(Player player, EditorSession session, int index, int page) {
        GuiTemplate template = context.screens.template("confirm");
        GuiHolder holder = new GuiHolder(GuiView.BOSS_PHASE_CONFIRM, AdminType.BOSS,
                Integer.toString(index), EditorCategory.BOSS_BASIC.key(), page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", index + 1));
        context.screens.fillStatic(inventory, template, 'y', player, Map.of("id", index + 1));
        context.screens.fillStatic(inventory, template, 'n', player, Map.of("id", index + 1));
        player.openInventory(inventory);
    }

    void handleBossPhaseConfirm(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        GuiTemplate template = context.screens.template("confirm");
        if (template.slots('n').contains(slot)) {
            openBossPhaseList(player, session, holder.page);
            return;
        }
        if (!template.slots('y').contains(slot)) {
            return;
        }
        if (BossPhaseDraft.count(session.yaml) <= 1) {
            context.messages.send(player, "gui.boss-phase-list.last-phase", Map.of());
            openBossPhaseList(player, session, holder.page);
            return;
        }
        try {
            EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
            BossPhaseDraft.remove(working.yaml, Integer.parseInt(holder.id));
            context.sessionService.persistSession(player, working, () -> {
                context.messages.send(player, "common.deleted", Map.of("id", Integer.parseInt(holder.id) + 1));
                context.sessionService.refreshSession(player, session,
                        refreshed -> openBossPhaseList(player, refreshed, holder.page));
            }, exception -> {
                context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openBossPhaseList(player, refreshed, holder.page));
            });
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            context.sessionService.refreshAfterFailure(player, session,
                    refreshed -> openBossPhaseList(player, refreshed, holder.page));
        }
    }
}
