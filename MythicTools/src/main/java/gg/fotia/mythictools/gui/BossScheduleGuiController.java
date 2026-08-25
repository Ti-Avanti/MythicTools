package gg.fotia.mythictools.gui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Boss 固定点时间计划：刷怪点列表、时区/默认间隔与时间窗口编辑。 */
final class BossScheduleGuiController {
    private final GuiContext context;

    BossScheduleGuiController(GuiContext context) {
        this.context = context;
    }

    void openBossPointScheduleList(Player player, EditorSession session, int requestedPage) {
        GuiTemplate template = context.screens.template("boss-point-schedule-list");
        ConfigurationSection points = session.yaml.getConfigurationSection("spawning.points");
        List<String> ids = points == null ? List.of() : points.getKeys(false).stream().sorted().toList();
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (ids.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(GuiView.BOSS_POINT_SCHEDULE_LIST, AdminType.BOSS, session.id, page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", session.id, "page", page + 1));
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < ids.size(); index++) {
            String pointId = ids.get(start + index);
            int slot = template.slots('e').get(index);
            ConfigurationSection windows = session.yaml.getConfigurationSection(
                    "spawning.points." + pointId + ".schedule.time-windows");
            int count = windows == null ? 0 : windows.getKeys(false).size();
            inventory.setItem(slot, template.item('e', player, Map.of(
                    "id", pointId, "windows", count)));
            holder.valuesBySlot.put(slot, pointId);
        }
        for (char symbol : new char[]{'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleBossPointScheduleList(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        String pointId = holder.valuesBySlot.get(slot);
        if (pointId != null) {
            openBossTimeWindowList(player, session, pointId, 0);
            return;
        }
        GuiTemplate template = context.screens.template("boss-point-schedule-list");
        if (template.slots('p').contains(slot)) {
            openBossPointScheduleList(player, session, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openBossPointScheduleList(player, session, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            context.core.openCategory(player, session);
        }
    }

    void openBossTimeWindowList(
            Player player, EditorSession session, String pointId, int requestedPage) {
        GuiTemplate template = context.screens.template("boss-time-window-list");
        ConfigurationSection windows = session.yaml.getConfigurationSection(timeWindowsPath(pointId));
        List<String> ids = windows == null ? List.of() : windows.getKeys(false).stream().sorted().toList();
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (ids.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_TIME_WINDOW_LIST, AdminType.BOSS, pointId, session.id, page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "point", pointId, "page", page + 1));
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < ids.size(); index++) {
            String windowId = ids.get(start + index);
            String path = timeWindowPath(pointId, windowId);
            int slot = template.slots('e').get(index);
            inventory.setItem(slot, template.item('e', player, Map.of(
                    "id", windowId,
                    "start", session.yaml.getString(path + ".start", ""),
                    "end", session.yaml.getString(path + ".end", ""),
                    "interval", session.yaml.getLong(path + ".interval-seconds", 0L))));
            holder.valuesBySlot.put(slot, windowId);
        }
        BossPointScheduleSettings scheduleSettings = BossPointScheduleSettings.load(session.yaml, pointId);
        Map<String, Object> scheduleVariables = Map.of(
                "timezone", scheduleSettings.timezone(),
                "fallback", scheduleSettings.fallbackIntervalSeconds());
        for (int slot : template.slots('z')) {
            inventory.setItem(slot, template.item('z', player, scheduleVariables));
        }
        for (int slot : template.slots('f')) {
            inventory.setItem(slot, template.item('f', player, scheduleVariables));
        }
        for (char symbol : new char[]{'p', 'b', 'c', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleBossTimeWindowList(Player player, GuiHolder holder, int slot, boolean deleteClick) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        String pointId = holder.id;
        String windowId = holder.valuesBySlot.get(slot);
        if (windowId != null) {
            if (deleteClick) {
                openBossTimeWindowConfirm(player, session, pointId, windowId, holder.page);
            } else {
                BossTimeWindowDraft draft = BossTimeWindowDraft.load(
                        session.yaml, timeWindowPath(pointId, windowId));
                context.sessions.timeWindowDrafts.put(player.getUniqueId(), draft);
                openBossTimeWindowEditor(player, session, pointId, windowId, draft);
            }
            return;
        }
        GuiTemplate template = context.screens.template("boss-time-window-list");
        if (template.slots('p').contains(slot)) {
            openBossTimeWindowList(player, session, pointId, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openBossTimeWindowList(player, session, pointId, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            openBossPointScheduleList(player, session, 0);
        } else if (template.slots('z').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.boss-time-window-list.timezone-input"), input -> {
                        final BossPointScheduleSettings updated;
                        try {
                            updated = BossPointScheduleSettings.load(session.yaml, pointId)
                                    .withTimezone(input);
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player, "gui.boss-time-window-list.invalid-timezone",
                                    Map.of("value", input.trim()));
                            openBossTimeWindowList(player, session, pointId, holder.page);
                            return;
                        }
                        persistBossPointScheduleSettings(player, session, pointId, holder.page, updated);
                    }, () -> openBossTimeWindowList(player, session, pointId, holder.page));
        } else if (template.slots('f').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.boss-time-window-list.fallback-input"), input -> {
                        final BossPointScheduleSettings updated;
                        try {
                            updated = BossPointScheduleSettings.load(session.yaml, pointId)
                                    .withFallbackInterval(input);
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player,
                                    "gui.boss-time-window-list.invalid-fallback", Map.of());
                            openBossTimeWindowList(player, session, pointId, holder.page);
                            return;
                        }
                        persistBossPointScheduleSettings(player, session, pointId, holder.page, updated);
                    }, () -> openBossTimeWindowList(player, session, pointId, holder.page));
        } else if (template.slots('c').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.boss-time-window-list.input-id"), input -> {
                        String id = input.trim();
                        if (!EditorTargets.SAFE_ID.matcher(id).matches()) {
                            context.messages.send(player, "common.invalid-id", Map.of());
                            openBossTimeWindowList(player, session, pointId, 0);
                            return;
                        }
                        if (session.yaml.contains(timeWindowPath(pointId, id))) {
                            context.messages.send(player, "gui.boss-time-window-list.duplicate-id",
                                    Map.of("id", id));
                            openBossTimeWindowList(player, session, pointId, 0);
                            return;
                        }
                        BossTimeWindowDraft draft = BossTimeWindowDraft.create();
                        context.sessions.timeWindowDrafts.put(player.getUniqueId(), draft);
                        openBossTimeWindowEditor(player, session, pointId, id, draft);
                    }, () -> openBossTimeWindowList(player, session, pointId, 0));
        }
    }

    private void persistBossPointScheduleSettings(
            Player player,
            EditorSession session,
            String pointId,
            int page,
            BossPointScheduleSettings settings) {
        try {
            EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
            settings.applyTo(working.yaml, pointId);
            context.sessionService.persistSession(player, working);
            context.messages.send(player, "common.saved", Map.of());
            context.sessionService.refreshSession(player, session,
                    refreshed -> openBossTimeWindowList(player, refreshed, pointId, page));
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            context.sessionService.refreshAfterFailure(player, session,
                    refreshed -> openBossTimeWindowList(player, refreshed, pointId, page));
        }
    }

    private void openBossTimeWindowEditor(
            Player player,
            EditorSession session,
            String pointId,
            String windowId,
            BossTimeWindowDraft draft) {
        GuiTemplate template = context.screens.template("boss-time-window-editor");
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_TIME_WINDOW_EDITOR, AdminType.BOSS, windowId, pointId, 0);
        Map<String, Object> variables = Map.of(
                "id", windowId,
                "start", draft.start(),
                "end", draft.end(),
                "interval", draft.intervalSeconds());
        Inventory inventory = context.screens.createInventory(holder, template, player, variables);
        for (char symbol : new char[]{'a', 'e', 'i', 'b', 's'}) {
            context.screens.fillStatic(inventory, template, symbol, player, variables);
        }
        player.openInventory(inventory);
    }

    void handleBossTimeWindowEditor(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        BossTimeWindowDraft draft = context.sessions.timeWindowDrafts.get(player.getUniqueId());
        if (session == null || draft == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        String pointId = holder.context;
        GuiTemplate template = context.screens.template("boss-time-window-editor");
        if (template.slots('a').contains(slot)) {
            editTimeWindowText(player, session, pointId, holder.id, draft,
                    "gui.boss-time-window-editor.start-name", draft::start);
        } else if (template.slots('e').contains(slot)) {
            editTimeWindowText(player, session, pointId, holder.id, draft,
                    "gui.boss-time-window-editor.end-name", draft::end);
        } else if (template.slots('i').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.boss-time-window-editor.interval-name"), input -> {
                        try {
                            draft.intervalSeconds(Long.parseLong(input.trim()));
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player, "input.invalid",
                                    Map.of("reason", exception.getMessage()));
                        }
                        openBossTimeWindowEditor(player, session, pointId, holder.id, draft);
                    }, () -> openBossTimeWindowEditor(player, session, pointId, holder.id, draft));
        } else if (template.slots('b').contains(slot)) {
            context.sessions.timeWindowDrafts.remove(player.getUniqueId());
            openBossTimeWindowList(player, session, pointId, 0);
        } else if (template.slots('s').contains(slot)) {
            try {
                EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
                draft.applyTo(working.yaml, timeWindowPath(pointId, holder.id));
                context.sessionService.persistSession(player, working);
                context.sessions.timeWindowDrafts.remove(player.getUniqueId());
                context.messages.send(player, "common.saved", Map.of());
                context.sessionService.refreshSession(player, session,
                        refreshed -> openBossTimeWindowList(player, refreshed, pointId, 0));
            } catch (IOException | RuntimeException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openBossTimeWindowEditor(player, refreshed, pointId, holder.id, draft));
            }
        }
    }

    private void editTimeWindowText(
            Player player,
            EditorSession session,
            String pointId,
            String windowId,
            BossTimeWindowDraft draft,
            String labelKey,
            Consumer<String> setter) {
        context.chatInput.begin(player, context.messages.text(player, labelKey), input -> {
            try {
                setter.accept(input.trim());
            } catch (IllegalArgumentException exception) {
                context.messages.send(player, "input.invalid", Map.of("reason", exception.getMessage()));
            }
            openBossTimeWindowEditor(player, session, pointId, windowId, draft);
        }, () -> openBossTimeWindowEditor(player, session, pointId, windowId, draft));
    }

    private void openBossTimeWindowConfirm(
            Player player, EditorSession session, String pointId, String windowId, int page) {
        GuiTemplate template = context.screens.template("confirm");
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_TIME_WINDOW_CONFIRM, AdminType.BOSS, windowId, pointId, page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", windowId));
        context.screens.fillStatic(inventory, template, 'y', player, Map.of("id", windowId));
        context.screens.fillStatic(inventory, template, 'n', player, Map.of("id", windowId));
        player.openInventory(inventory);
    }

    void handleBossTimeWindowConfirm(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        GuiTemplate template = context.screens.template("confirm");
        if (template.slots('y').contains(slot)) {
            try {
                EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
                working.yaml.set(timeWindowPath(holder.context, holder.id), null);
                context.sessionService.persistSession(player, working);
                context.messages.send(player, "common.deleted", Map.of("id", holder.id));
                context.sessionService.refreshSession(player, session,
                        refreshed -> openBossTimeWindowList(player, refreshed, holder.context, holder.page));
            } catch (IOException | RuntimeException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openBossTimeWindowList(player, refreshed, holder.context, holder.page));
            }
        } else if (template.slots('n').contains(slot)) {
            openBossTimeWindowList(player, session, holder.context, holder.page);
        }
    }

    private static String timeWindowsPath(String pointId) {
        return "spawning.points." + pointId + ".schedule.time-windows";
    }

    private static String timeWindowPath(String pointId, String windowId) {
        return timeWindowsPath(pointId) + "." + windowId;
    }
}
