package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.version.BiomeKeys;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Boss 群系/固定点生成器的列表、编辑、创建与删除确认。 */
final class BossSpawnerGuiController {
    private final GuiContext context;

    BossSpawnerGuiController(GuiContext context) {
        this.context = context;
    }

    static boolean isBossSpawnerCategory(EditorCategory category) {
        return category == EditorCategory.BOSS_BIOME_SPAWNING
                || category == EditorCategory.BOSS_POINT_SPAWNING;
    }

    void openBossSpawnerList(
            Player player,
            EditorSession session,
            EditorCategory category,
            int requestedPage) {
        GuiTemplate template = context.screens.template("boss-spawner-list");
        List<String> ids = BossSpawnerEditorFields.ids(session.yaml, category);
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (ids.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_SPAWNER_LIST, AdminType.BOSS, session.id, category.key(), page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "boss", session.id,
                "category", context.screens.categoryName(player, category),
                "page", page + 1));
        int start = page * pageSize;
        List<Integer> slots = template.slots('e');
        for (int index = 0; index < slots.size() && start + index < ids.size(); index++) {
            String spawnerId = ids.get(start + index);
            int slot = slots.get(index);
            boolean enabled = session.yaml.getBoolean(
                    BossSpawnerEditorFields.spawnerPath(category, spawnerId) + ".enabled", true);
            ItemStack item = template.item('e', player, Map.of(
                    "id", spawnerId,
                    "status", context.messages.text(player, "gui.boss-spawner-list.status-"
                            + (enabled ? "enabled" : "disabled"))));
            if (template.usesSemanticMaterial('e')) {
                item.setType(category == EditorCategory.BOSS_BIOME_SPAWNING
                        ? Material.GRASS_BLOCK : Material.LODESTONE);
            }
            inventory.setItem(slot, item);
            holder.valuesBySlot.put(slot, spawnerId);
        }
        for (char symbol : new char[]{'p', 'b', 'c', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleBossSpawnerList(Player player, GuiHolder holder, int slot, boolean deleteClick) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || holder.context == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        EditorCategory category = EditorCategory.byKey(AdminType.BOSS, holder.context);
        String spawnerId = holder.valuesBySlot.get(slot);
        if (spawnerId != null) {
            if (deleteClick) {
                openBossSpawnerConfirm(player, session, category, spawnerId, holder.page);
            } else {
                session.page = 0;
                openBossSpawnerEditor(player, session, category, spawnerId);
            }
            return;
        }
        GuiTemplate template = context.screens.template("boss-spawner-list");
        if (template.slots('p').contains(slot)) {
            openBossSpawnerList(player, session, category, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openBossSpawnerList(player, session, category, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            context.core.openCategory(player, session);
        } else if (template.slots('c').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.boss-spawner-list.input-id"),
                    input -> createBossSpawner(player, session, category, input),
                    () -> openBossSpawnerList(player, session, category, holder.page));
        }
    }

    private void createBossSpawner(
            Player player, EditorSession session, EditorCategory category, String input) {
        String spawnerId = input == null ? "" : input.trim();
        if (!EditorTargets.SAFE_ID.matcher(spawnerId).matches()) {
            context.messages.send(player, "common.invalid-id", Map.of());
            openBossSpawnerList(player, session, category, 0);
            return;
        }
        if (session.yaml.contains(BossSpawnerEditorFields.spawnerPath(category, spawnerId))) {
            context.messages.send(player, "gui.boss-spawner-list.duplicate-id", Map.of("id", spawnerId));
            openBossSpawnerList(player, session, category, 0);
            return;
        }
        try {
            Location location = player.getLocation();
            if (location.getWorld() == null) {
                throw new IllegalStateException(
                        context.messages.text(player, "gui.boss-spawner-list.world-unavailable"));
            }
            EditorSession working;
            if (category == EditorCategory.BOSS_BIOME_SPAWNING) {
                working = BossSpawnerDrafts.createBiome(
                        session,
                        spawnerId,
                        location.getWorld().getName(),
                        BiomeKeys.name(location.getBlock().getBiome()));
            } else {
                working = BossSpawnerDrafts.createPoint(
                        session,
                        spawnerId,
                        location.getWorld().getName(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch(),
                        ZoneId.systemDefault().getId());
            }
            context.sessions.editors.put(player.getUniqueId(), working);
            openBossSpawnerEditor(player, working, category, spawnerId);
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            context.sessions.editors.put(player.getUniqueId(), session);
            openBossSpawnerList(player, session, category, 0);
        }
    }

    void openBossSpawnerEditor(
            Player player, EditorSession session, EditorCategory category, String spawnerId) {
        GuiTemplate template = context.screens.template("boss-spawner-editor");
        List<String> fields;
        try {
            fields = BossSpawnerEditorFields.fields(session.yaml, category, spawnerId);
        } catch (IllegalArgumentException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            openBossSpawnerList(player, session, category, 0);
            return;
        }
        int pageSize = template.slots('f').size();
        int maximumPage = Math.max(0, (fields.size() - 1) / pageSize);
        session.page = Math.max(0, Math.min(maximumPage, session.page));
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_SPAWNER_EDITOR, AdminType.BOSS, spawnerId, category.key(), session.page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "boss", session.id,
                "spawner", spawnerId,
                "category", context.screens.categoryName(player, category),
                "page", session.page + 1));
        int start = session.page * pageSize;
        List<Integer> slots = template.slots('f');
        for (int index = 0; index < slots.size() && start + index < fields.size(); index++) {
            String field = fields.get(start + index);
            String relative = BossSpawnerEditorFields.relativeField(category, spawnerId, field);
            ItemStack item = template.item('f', player, Map.of(
                    "field", context.values.fieldLabel(player, relative),
                    "value", EditorValueFormats.formatValue(
                            EditorValueFormats.editorValue(session, field))));
            if (template.usesSemanticMaterial('f')) {
                item.setType(EditorFieldIcon.forField(
                        relative, EditorValueFormats.editorValue(session, field)));
            }
            int slot = slots.get(index);
            inventory.setItem(slot, item);
            holder.valuesBySlot.put(slot, field);
        }
        for (char symbol : new char[]{'p', 'b', 's', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleBossSpawnerEditor(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || holder.context == null || holder.id == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        EditorCategory category = EditorCategory.byKey(AdminType.BOSS, holder.context);
        String spawnerId = holder.id;
        GuiTemplate template = context.screens.template("boss-spawner-editor");
        String field = holder.valuesBySlot.get(slot);
        if (field != null) {
            Object current = EditorValueFormats.editorValue(session, field);
            String relative = BossSpawnerEditorFields.relativeField(category, spawnerId, field);
            String label = context.values.fieldLabel(player, relative);
            if (BooleanFieldToggle.apply(session.yaml, EditorValueFormats.absolutePath(session, field))) {
                openBossSpawnerEditor(player, session, category, spawnerId);
                return;
            }
            FieldSelectorType selectorType = FieldSelectorType.fromField(field);
            if (selectorType != null) {
                context.selector.openSelector(player, session, field, selectorType, category, 0);
                return;
            }
            context.chatInput.begin(player,
                    label + " (" + context.values.inputHint(player, current) + ")", input -> {
                        try {
                            session.yaml.set(EditorValueFormats.absolutePath(session, field),
                                    context.values.parseValue(player, current, input));
                            context.messages.send(player, "input.accepted",
                                    Map.of("field", label, "value", input));
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player, "input.invalid",
                                    Map.of("reason", exception.getMessage()));
                        }
                        openBossSpawnerEditor(player, session, category, spawnerId);
                    }, () -> openBossSpawnerEditor(player, session, category, spawnerId));
            return;
        }
        if (template.slots('p').contains(slot)) {
            session.page = Math.max(0, session.page - 1);
            openBossSpawnerEditor(player, session, category, spawnerId);
        } else if (template.slots('n').contains(slot)) {
            session.page++;
            openBossSpawnerEditor(player, session, category, spawnerId);
        } else if (template.slots('b').contains(slot)) {
            try {
                context.sessionService.refreshSession(player, session, true,
                        refreshed -> openBossSpawnerList(player, refreshed, category, 0));
            } catch (IOException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
                context.core.openList(player, AdminType.BOSS, 0);
            }
        } else if (template.slots('s').contains(slot)) {
            saveBossSpawner(player, session, category, spawnerId);
        }
    }

    private void saveBossSpawner(
            Player player, EditorSession session, EditorCategory category, String spawnerId) {
        try {
            context.sessionService.persistSession(player, session);
            context.messages.send(player, "common.saved", Map.of());
            context.sessionService.refreshSession(player, session,
                    refreshed -> openBossSpawnerList(player, refreshed, category, 0));
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            openBossSpawnerEditor(player, session, category, spawnerId);
        }
    }

    private void openBossSpawnerConfirm(
            Player player,
            EditorSession session,
            EditorCategory category,
            String spawnerId,
            int page) {
        GuiTemplate template = context.screens.template("confirm");
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_SPAWNER_CONFIRM, AdminType.BOSS, spawnerId, category.key(), page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", spawnerId));
        context.screens.fillStatic(inventory, template, 'y', player, Map.of("id", spawnerId));
        context.screens.fillStatic(inventory, template, 'n', player, Map.of("id", spawnerId));
        player.openInventory(inventory);
    }

    void handleBossSpawnerConfirm(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || holder.context == null || holder.id == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        EditorCategory category = EditorCategory.byKey(AdminType.BOSS, holder.context);
        GuiTemplate template = context.screens.template("confirm");
        if (template.slots('y').contains(slot)) {
            try {
                EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
                BossSpawnerEditorFields.remove(working.yaml, category, holder.id);
                context.sessionService.persistSession(player, working);
                context.messages.send(player, "common.deleted", Map.of("id", holder.id));
                context.sessionService.refreshSession(player, session,
                        refreshed -> openBossSpawnerList(player, refreshed, category, holder.page));
            } catch (IOException | RuntimeException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openBossSpawnerList(player, refreshed, category, holder.page));
            }
        } else if (template.slots('n').contains(slot)) {
            openBossSpawnerList(player, session, category, holder.page);
        }
    }
}
