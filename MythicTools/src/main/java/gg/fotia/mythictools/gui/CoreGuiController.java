package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.ReloadRollback;
import gg.fotia.mythictools.runtime.ActiveRuntimeStateException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** 主菜单、分类导航、通用字段编辑器与通用选择器/确认页。 */
final class CoreGuiController {
    private final GuiContext context;

    CoreGuiController(GuiContext context) {
        this.context = context;
    }

    void openMain(Player player) {
        context.sessionService.resetEditingState(player.getUniqueId());
        GuiTemplate template = context.screens.template("main");
        GuiHolder holder = new GuiHolder(GuiView.MAIN, null, null, 0);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of());
        for (char symbol : new char[]{'d', 's', 'b', 'r', 'c'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void openDropsMenu(Player player) {
        GuiTemplate template = context.screens.template("drops-menu");
        GuiHolder holder = new GuiHolder(GuiView.DROPS_MENU, null, null, 0);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of());
        for (char symbol : new char[]{'g', 'm', 'r'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void openSpawningMenu(Player player) {
        GuiTemplate template = context.screens.template("spawning-menu");
        GuiHolder holder = new GuiHolder(GuiView.SPAWNING_MENU, null, null, 0);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of());
        for (char symbol : new char[]{'b', 'p', 'g', 'r'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void openList(Player player, AdminType type, int requestedPage) {
        GuiTemplate template = context.screens.template("list");
        List<String> ids = context.targets.ids(type);
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (ids.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        Map<String, Object> titleVariables = Map.of(
                "type", context.screens.typeLabel(player, type), "page", page + 1);
        GuiHolder holder = new GuiHolder(GuiView.LIST, type, null, page);
        Inventory inventory = context.screens.createInventory(holder, template, player, titleVariables);
        List<Integer> contentSlots = template.slots('e');
        int start = page * pageSize;
        for (int index = 0; index < contentSlots.size() && start + index < ids.size(); index++) {
            String id = ids.get(start + index);
            int slot = contentSlots.get(index);
            inventory.setItem(slot, template.item('e', player, Map.of("id", id)));
            holder.valuesBySlot.put(slot, id);
        }
        for (char symbol : new char[]{'p', 'b', 'c', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void openEditor(Player player, AdminType type, String id) {
        context.sessionService.resetEditingState(player.getUniqueId());
        try {
            EditorSession session = context.targets.loadSession(type, id);
            context.sessions.editors.put(player.getUniqueId(), session);
            if (type == AdminType.SPAWN_POINT) {
                openEditorSession(player, session, null);
            } else {
                openCategory(player, session);
            }
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
        }
    }

    void handleMain(Player player, int slot) {
        GuiTemplate template = context.screens.template("main");
        if (template.slots('d').contains(slot)) {
            openDropsMenu(player);
        } else if (template.slots('s').contains(slot)) {
            openSpawningMenu(player);
        } else if (template.slots('b').contains(slot)) {
            openList(player, AdminType.BOSS, 0);
        } else if (template.slots('r').contains(slot)) {
            try {
                context.fullReloadAction.accept(player);
            } catch (ActiveRuntimeStateException exception) {
                context.messages.send(player, "command.reload-active", Map.of(
                        "spawning", exception.spawningEntities(),
                        "bosses", exception.bossFights()));
            } catch (RuntimeException exception) {
                context.messages.send(player, "command.reload-failed",
                        Map.of("reason", exception.getMessage()));
            }
        } else if (template.slots('c').contains(slot)) {
            player.closeInventory();
        }
    }

    void handleDropsMenu(Player player, int slot) {
        GuiTemplate template = context.screens.template("drops-menu");
        if (template.slots('g').contains(slot)) {
            openList(player, AdminType.DROP_GROUP, 0);
        } else if (template.slots('m').contains(slot)) {
            openList(player, AdminType.MOB_DROP, 0);
        } else if (template.slots('r').contains(slot)) {
            openMain(player);
        }
    }

    void handleSpawningMenu(Player player, int slot) {
        GuiTemplate template = context.screens.template("spawning-menu");
        if (template.slots('b').contains(slot)) {
            openList(player, AdminType.BIOME_RULE, 0);
        } else if (template.slots('p').contains(slot)) {
            openList(player, AdminType.SPAWN_POINT, 0);
        } else if (template.slots('g').contains(slot)) {
            openList(player, AdminType.MOB_GROUP, 0);
        } else if (template.slots('r').contains(slot)) {
            openMain(player);
        }
    }

    void handleList(Player player, GuiHolder holder, int slot, boolean deleteClick) {
        GuiTemplate template = context.screens.template("list");
        String id = holder.valuesBySlot.get(slot);
        if (id != null) {
            if (deleteClick) {
                openConfirm(player, holder.type, id, holder.page);
            } else {
                openEditor(player, holder.type, id);
            }
            return;
        }
        if (template.slots('p').contains(slot)) {
            openList(player, holder.type, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openList(player, holder.type, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            if (holder.type == AdminType.DROP_GROUP || holder.type == AdminType.MOB_DROP) {
                openDropsMenu(player);
            } else if (holder.type == AdminType.BIOME_RULE || holder.type == AdminType.SPAWN_POINT
                    || holder.type == AdminType.MOB_GROUP) {
                openSpawningMenu(player);
            } else {
                openMain(player);
            }
        } else if (template.slots('c').contains(slot)) {
            context.chatInput.begin(player, "ID", input -> createAndOpen(player, holder.type, input),
                    () -> openList(player, holder.type, holder.page));
        }
    }

    void handleEditor(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null) {
            openList(player, holder.type, 0);
            return;
        }
        EditorCategory category = holder.context == null
                ? null : EditorCategory.byKey(session.type, holder.context);
        GuiTemplate template = context.screens.template(category == null ? "editor" : "section-editor");
        String field = holder.valuesBySlot.get(slot);
        if (field != null) {
            if (session.type == AdminType.BOSS && field.equals("phases")) {
                context.sessions.bossPhaseDrafts.remove(player.getUniqueId());
                context.bossPhases.openBossPhaseList(player, session, 0);
                return;
            }
            Object current = EditorValueFormats.editorValue(session, field);
            var toggled = BooleanFieldToggle.next(current);
            if (toggled.isPresent()) {
                session.yaml.set(EditorValueFormats.absolutePath(session, field), toggled.get());
                openEditorSession(player, session, category);
                return;
            }
            String label = context.values.fieldLabel(player, field);
            FieldSelectorType selectorType = FieldSelectorType.fromField(field);
            if (selectorType != null) {
                context.selector.openSelector(player, session, field, selectorType, category, 0);
                return;
            }
            context.chatInput.begin(player,
                    label + " (" + context.values.inputHint(player, current) + ")", input -> {
                        try {
                            Object parsed = context.values.parseValue(player, current, input);
                            session.yaml.set(EditorValueFormats.absolutePath(session, field), parsed);
                            if (field.equals("mob") && parsed != null && !String.valueOf(parsed).isBlank()) {
                                session.yaml.set(EditorValueFormats.absolutePath(session, "mob-group"), null);
                            }
                            context.messages.send(player, "input.accepted",
                                    Map.of("field", label, "value", input));
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player, "input.invalid",
                                    Map.of("reason", exception.getMessage()));
                        }
                        openEditorSession(player, session, category);
                    }, () -> openEditorSession(player, session, category));
            return;
        }
        if (template.slots('p').contains(slot)) {
            session.page = Math.max(0, session.page - 1);
            openEditorSession(player, session, category);
        } else if (template.slots('n').contains(slot)) {
            session.page++;
            openEditorSession(player, session, category);
        } else if (template.slots('b').contains(slot)) {
            if (category == null) {
                try {
                    context.sessionService.discardNewUnchangedTarget(session);
                } catch (IOException exception) {
                    context.messages.send(player, "common.config-error",
                            Map.of("reason", exception.getMessage()));
                    return;
                }
                context.sessions.editors.remove(player.getUniqueId());
                openList(player, session.type, 0);
            } else {
                discardChangesAndOpenCategory(player, session);
            }
        } else if (template.slots('s').contains(slot)) {
            if (category == null) {
                save(player, session);
            } else {
                saveSection(player, session, category);
            }
        }
    }

    void handleCategory(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null) {
            openList(player, holder.type, 0);
            return;
        }
        String categoryKey = holder.valuesBySlot.get(slot);
        if (categoryKey != null) {
            EditorCategory category = EditorCategory.byKey(session.type, categoryKey);
            session.page = 0;
            if (session.type == AdminType.DROP_GROUP) {
                context.rewards.openRewardList(player, session, category, 0);
            } else if (session.type == AdminType.MOB_GROUP
                    && category == EditorCategory.MOB_GROUP_MEMBERS) {
                context.mobMembers.openMobMemberList(player, session, 0);
            } else if (session.type == AdminType.MOB_DROP
                    && category == EditorCategory.MOB_DROP_GROUPS) {
                context.mobDropGroups.openMobDropGroupList(player, session, 0);
            } else if ((session.type == AdminType.MOB_DROP
                    && category == EditorCategory.MOB_DROP_FIRST_DEFEAT)
                    || (session.type == AdminType.BOSS
                    && category == EditorCategory.BOSS_FIRST_DEFEAT)) {
                context.firstDefeats.openMenu(player, session);
            } else if (session.type == AdminType.BOSS
                    && category == EditorCategory.BOSS_POINT_SCHEDULE) {
                context.bossSchedules.openBossPointScheduleList(player, session, 0);
            } else if (session.type == AdminType.BOSS
                    && category == EditorCategory.BOSS_RANKING_REWARDS) {
                context.bossRewards.openBossRankingRewardMenu(player, session);
            } else if (session.type == AdminType.BOSS
                    && category == EditorCategory.BOSS_KILLER_REWARDS) {
                context.bossRewards.openBossRewardGroupList(player, session, "killer", 0);
            } else if (session.type == AdminType.BOSS
                    && BossSpawnerGuiController.isBossSpawnerCategory(category)) {
                context.bossSpawners.openBossSpawnerList(player, session, category, 0);
            } else {
                openEditorSession(player, session, category);
            }
            return;
        }
        GuiTemplate template = context.screens.template("category");
        if (template.slots('b').contains(slot)) {
            try {
                context.sessionService.discardNewUnchangedTarget(session);
            } catch (IOException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
                return;
            }
            context.sessions.editors.remove(player.getUniqueId());
            context.sessions.clearDrafts(player.getUniqueId());
            openList(player, session.type, 0);
        }
    }

    void openCategory(Player player, EditorSession session) {
        GuiTemplate template = context.screens.template("category");
        GuiHolder holder = new GuiHolder(GuiView.CATEGORY, session.type, session.id, 0);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", session.id));
        List<Integer> slots = template.slots('g');
        List<EditorCategory> categories = EditorCategory.forType(session.type);
        for (int index = 0; index < slots.size() && index < categories.size(); index++) {
            EditorCategory category = categories.get(index);
            int slot = slots.get(index);
            ItemStack item = template.item('g', player, Map.of(
                    "name", context.screens.categoryName(player, category),
                    "description", context.screens.categoryDescription(player, category)));
            if (template.usesSemanticMaterial('g')) {
                item.setType(EditorCategoryIcon.forKey(category.key()));
            }
            inventory.setItem(slot, item);
            holder.valuesBySlot.put(slot, category.key());
        }
        context.screens.fillStatic(inventory, template, 'b', player, Map.of());
        player.openInventory(inventory);
    }

    void openEditorSession(Player player, EditorSession session, EditorCategory category) {
        GuiTemplate template = context.screens.template(category == null ? "editor" : "section-editor");
        List<String> fields = editableFields(session, category);
        int pageSize = template.slots('f').size();
        int maximumPage = Math.max(0, (fields.size() - 1) / pageSize);
        session.page = Math.max(0, Math.min(maximumPage, session.page));
        GuiHolder holder = new GuiHolder(category == null ? GuiView.EDITOR : GuiView.SECTION_EDITOR,
                session.type, session.id, category == null ? null : category.key(), session.page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("type", context.screens.typeLabel(player, session.type), "id", session.id,
                        "category", category == null ? context.screens.typeLabel(player, session.type)
                                : context.screens.categoryName(player, category)));
        int start = session.page * pageSize;
        for (int index = 0; index < pageSize && start + index < fields.size(); index++) {
            String field = fields.get(start + index);
            int slot = template.slots('f').get(index);
            inventory.setItem(slot, context.screens.editorFieldItem(
                    template, player, field, EditorValueFormats.editorValue(session, field)));
            holder.valuesBySlot.put(slot, field);
        }
        for (char symbol : new char[]{'p', 'b', 's', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void openConfirm(Player player, AdminType type, String id, int page) {
        GuiTemplate template = context.screens.template("confirm");
        GuiHolder holder = new GuiHolder(GuiView.CONFIRM, type, id, page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of("id", id));
        context.screens.fillStatic(inventory, template, 'y', player, Map.of("id", id));
        context.screens.fillStatic(inventory, template, 'n', player, Map.of("id", id));
        player.openInventory(inventory);
    }

    void handleConfirm(Player player, GuiHolder holder, int slot) {
        GuiTemplate template = context.screens.template("confirm");
        if (template.slots('y').contains(slot)) {
            try {
                if (holder.type == AdminType.MOB_GROUP) {
                    List<String> references = context.targets.mobGroupReferences(holder.id);
                    if (!references.isEmpty()) {
                        context.messages.send(player, "gui.mob-group.in-use",
                                Map.of("references", String.join(", ", references)));
                        openList(player, holder.type, holder.page);
                        return;
                    }
                }
                EditorTargets.Target deletionTarget = context.targets.target(holder.type, holder.id);
                try {
                    ReloadRollback.mutate(
                            deletionTarget.file().toPath(),
                            () -> context.targets.deleteTarget(holder.type, holder.id),
                            () -> context.saveReloadAction.accept(holder.type));
                } catch (RuntimeException exception) {
                    throw new IllegalStateException(
                            context.messages.text(player, "common.save-rolled-back"), exception);
                }
                context.messages.send(player, "common.deleted", Map.of("id", holder.id));
            } catch (IOException | RuntimeException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
            }
            openList(player, holder.type, holder.page);
        } else if (template.slots('n').contains(slot)) {
            openList(player, holder.type, holder.page);
        }
    }

    void createAndOpen(Player player, AdminType type, String input) {
        String id = input.trim();
        if (!EditorTargets.SAFE_ID.matcher(id).matches()) {
            context.messages.send(player, "common.invalid-id", Map.of());
            openList(player, type, 0);
            return;
        }
        boolean created = false;
        EditorSession session = null;
        try {
            context.targets.createTarget(type, id, player.getLocation());
            created = true;
            session = context.targets.loadSession(type, id);
            session.markNewlyCreated();
            context.sessions.editors.put(player.getUniqueId(), session);
            if (type == AdminType.SPAWN_POINT) {
                openEditorSession(player, session, null);
            } else {
                openCategory(player, session);
            }
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            context.sessions.editors.remove(player.getUniqueId());
            context.sessions.clearDrafts(player.getUniqueId());
            if (created) {
                try {
                    if (session != null) {
                        context.sessionService.discardNewUnchangedTarget(session);
                    } else {
                        context.targets.deleteTarget(type, id);
                    }
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                    context.plugin.getLogger().warning("清理未打开的新建配置失败: " + cleanupException.getMessage());
                }
            }
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            openList(player, type, 0);
        }
    }

    void save(Player player, EditorSession session) {
        try {
            context.sessionService.persistSession(player, session, () -> {
                context.sessions.editors.remove(player.getUniqueId());
                context.messages.send(player, "common.saved", Map.of());
                openList(player, session.type, 0);
            }, exception -> {
                context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
                openEditorSession(player, session, null);
            });
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            openEditorSession(player, session, null);
        }
    }

    void saveSection(Player player, EditorSession session, EditorCategory category) {
        try {
            context.sessionService.persistSession(player, session, () -> {
                context.messages.send(player, "common.saved", Map.of());
                context.sessionService.refreshSession(player, session, refreshed -> openCategory(player, refreshed));
            }, exception -> {
                context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
                openEditorSession(player, session, category);
            });
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            openEditorSession(player, session, category);
        }
    }

    void discardChangesAndOpenCategory(Player player, EditorSession session) {
        try {
            context.sessionService.refreshSession(player, session, true,
                    refreshed -> openCategory(player, refreshed));
        } catch (IOException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            openList(player, session.type, 0);
        }
    }

    private List<String> editableFields(EditorSession session, EditorCategory category) {
        return EditorFieldCatalog.fields(session, category);
    }
}
