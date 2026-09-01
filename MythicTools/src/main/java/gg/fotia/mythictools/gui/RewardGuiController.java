package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.reward.Rarity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** 掉落组奖励条目的列表、编辑、稀有度/选项选择与删除确认。 */
final class RewardGuiController {
    private final GuiContext context;

    RewardGuiController(GuiContext context) {
        this.context = context;
    }

    void handleRewardList(Player player, GuiHolder holder, int slot, boolean deleteClick) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || holder.context == null) {
            context.core.openList(player, AdminType.DROP_GROUP, 0);
            return;
        }
        EditorCategory category = EditorCategory.byKey(AdminType.DROP_GROUP, holder.context);
        String entryId = holder.valuesBySlot.get(slot);
        if (entryId != null) {
            if (deleteClick) {
                openRewardConfirm(player, session, category, entryId, holder.page);
            } else {
                RewardEntryDraft draft = RewardEntryDraft.load(session.yaml, "entries." + entryId);
                context.sessions.rewardDrafts.put(player.getUniqueId(), draft);
                session.page = 0;
                openRewardEditor(player, session, category, entryId, draft);
            }
            return;
        }
        GuiTemplate template = context.screens.template("reward-list");
        if (template.slots('p').contains(slot)) {
            openRewardList(player, session, category, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openRewardList(player, session, category, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            context.core.openCategory(player, session);
        } else if (template.slots('i').contains(slot) && category.acceptsRewardType("item")) {
            beginCreateReward(player, session, category, "item");
        } else if (template.slots('c').contains(slot) && category.acceptsRewardType("command")) {
            beginCreateReward(player, session, category, "command");
        }
    }

    void handleRewardEditor(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        RewardEntryDraft draft = context.sessions.rewardDrafts.get(player.getUniqueId());
        if (session == null || draft == null || holder.context == null) {
            context.core.openList(player, AdminType.DROP_GROUP, 0);
            return;
        }
        EditorCategory category = EditorCategory.byKey(AdminType.DROP_GROUP, holder.context);
        GuiTemplate template = context.screens.template(draft.type().equals("item")
                ? "item-reward-editor" : "command-reward-editor");
        if (draft.type().equals("item") && template.slots('i').contains(slot)) {
            ItemStack cursor = player.getItemOnCursor();
            draft.item(cursor == null || cursor.getType().isAir() ? null : cursor);
            holder.getInventory().setItem(slot, rewardEditorItem(template, player, draft));
            return;
        }
        String field = holder.valuesBySlot.get(slot);
        if (field != null) {
            if (field.equals("rarity")) {
                openRewardRaritySelector(player, session, category, holder.id, draft, 0);
                return;
            }
            RewardOptionSelectorType optionSelector = RewardOptionSelectorType.fromField(field);
            if (optionSelector != null) {
                openRewardOptionSelector(player, session, category, holder.id, draft, optionSelector);
                return;
            }
            Object current = draft.value(field);
            var toggled = BooleanFieldToggle.next(current);
            if (toggled.isPresent()) {
                draft.set(field, toggled.get());
                openRewardEditor(player, session, category, holder.id, draft);
                return;
            }
            String label = context.values.fieldLabel(player, field);
            context.chatInput.begin(player,
                    label + " (" + context.values.inputHint(player, current) + ")", input -> {
                        try {
                            draft.set(field, context.values.parseValue(player, current, input));
                            context.messages.send(player, "input.accepted",
                                    Map.of("field", label, "value", input));
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player, "input.invalid",
                                    Map.of("reason", exception.getMessage()));
                        }
                        openRewardEditor(player, session, category, holder.id, draft);
                    }, () -> openRewardEditor(player, session, category, holder.id, draft));
            return;
        }
        if (template.slots('p').contains(slot)) {
            session.page = Math.max(0, session.page - 1);
            openRewardEditor(player, session, category, holder.id, draft);
        } else if (template.slots('n').contains(slot)) {
            session.page++;
            openRewardEditor(player, session, category, holder.id, draft);
        } else if (template.slots('b').contains(slot)) {
            context.sessions.rewardDrafts.remove(player.getUniqueId());
            openRewardList(player, session, category, 0);
        } else if (template.slots('s').contains(slot)) {
            saveReward(player, session, category, holder.id, draft);
        }
    }

    private void openRewardRaritySelector(
            Player player,
            EditorSession session,
            EditorCategory category,
            String entryId,
            RewardEntryDraft draft,
            int requestedPage) {
        GuiTemplate template = context.screens.template("reward-rarity-selector");
        List<Rarity> rarities = context.configuredRarities.get();
        int pageSize = template.slots('u').size() + template.slots('s').size();
        int maximumPage = Math.max(0, (rarities.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(GuiView.REWARD_RARITY_SELECTOR, AdminType.DROP_GROUP,
                entryId, category.key(), page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", entryId, "page", page + 1));
        List<Integer> slots = GuiScreenSupport.selectorContentSlots(template);
        String selectedId = String.valueOf(draft.value("rarity"));
        int start = page * pageSize;
        for (int index = 0; index < slots.size() && start + index < rarities.size(); index++) {
            Rarity rarity = rarities.get(start + index);
            int slot = slots.get(index);
            boolean selected = rarity.id().equals(selectedId);
            inventory.setItem(slot, rewardRarityItem(template, player, rarity, selected));
            holder.valuesBySlot.put(slot, rarity.id());
        }
        for (char symbol : new char[]{'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleRewardRaritySelector(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        RewardEntryDraft draft = context.sessions.rewardDrafts.get(player.getUniqueId());
        if (session == null || draft == null || holder.context == null) {
            context.core.openList(player, AdminType.DROP_GROUP, 0);
            return;
        }
        EditorCategory category = EditorCategory.byKey(AdminType.DROP_GROUP, holder.context);
        String rarityId = holder.valuesBySlot.get(slot);
        if (rarityId != null) {
            draft.set("rarity", rarityId);
            openRewardEditor(player, session, category, holder.id, draft);
            return;
        }
        GuiTemplate template = context.screens.template("reward-rarity-selector");
        if (template.slots('p').contains(slot)) {
            openRewardRaritySelector(player, session, category, holder.id, draft, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openRewardRaritySelector(player, session, category, holder.id, draft, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            openRewardEditor(player, session, category, holder.id, draft);
        }
    }

    private void openRewardOptionSelector(
            Player player,
            EditorSession session,
            EditorCategory category,
            String entryId,
            RewardEntryDraft draft,
            RewardOptionSelectorType selector) {
        GuiTemplate template = context.screens.template(optionTemplate(selector));
        GuiHolder holder = new GuiHolder(GuiView.REWARD_OPTION_SELECTOR, AdminType.DROP_GROUP,
                entryId, category.key(), selector.field(), 0);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "id", entryId,
                "mode", context.messages.text(player,
                        "gui.reward-option-selector." + selector.field() + "-label")));
        String current = String.valueOf(draft.value(selector.field()));
        for (RewardOptionSelectorType.Option option : selector.options()) {
            boolean selected = option.value().equals(current);
            Map<String, Object> variables = Map.of(
                    "value", option.value(),
                    "status", context.messages.text(player, selected
                            ? "gui.reward-option-selector.selected"
                            : "gui.reward-option-selector.choose"));
            for (int optionSlot : template.slots(option.symbol())) {
                inventory.setItem(optionSlot, template.item(option.symbol(), player, variables));
                holder.valuesBySlot.put(optionSlot, option.value());
            }
        }
        context.screens.fillStatic(inventory, template, 'b', player, Map.of());
        player.openInventory(inventory);
    }

    void handleRewardOptionSelector(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        RewardEntryDraft draft = context.sessions.rewardDrafts.get(player.getUniqueId());
        if (session == null || draft == null || holder.context == null) {
            context.core.openDropsMenu(player);
            return;
        }
        EditorCategory category = EditorCategory.byKey(AdminType.DROP_GROUP, holder.context);
        RewardOptionSelectorType selector = RewardOptionSelectorType.fromField(holder.selection);
        if (selector == null) {
            openRewardEditor(player, session, category, holder.id, draft);
            return;
        }
        String option = holder.valuesBySlot.get(slot);
        if (option != null) {
            draft.set(selector.field(), option);
            openRewardEditor(player, session, category, holder.id, draft);
            return;
        }
        GuiTemplate template = context.screens.template(optionTemplate(selector));
        if (template.slots('b').contains(slot)) {
            openRewardEditor(player, session, category, holder.id, draft);
        }
    }

    private static String optionTemplate(RewardOptionSelectorType selector) {
        return selector == RewardOptionSelectorType.GRANT_MODE
                ? "reward-grant-mode-selector" : "reward-option-selector";
    }

    void handleRewardConfirm(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || holder.context == null) {
            context.core.openList(player, AdminType.DROP_GROUP, 0);
            return;
        }
        EditorCategory category = EditorCategory.byKey(AdminType.DROP_GROUP, holder.context);
        GuiTemplate template = context.screens.template("confirm");
        if (template.slots('y').contains(slot)) {
            try {
                EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
                working.yaml.set("entries." + holder.id, null);
                context.sessionService.persistSession(player, working);
                context.messages.send(player, "common.deleted", Map.of("id", holder.id));
                context.sessionService.refreshSession(player, session,
                        refreshed -> openRewardList(player, refreshed, category, holder.page));
            } catch (IOException | RuntimeException exception) {
                context.messages.send(player, "common.config-error",
                        Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session,
                        refreshed -> openRewardList(player, refreshed, category, holder.page));
            }
        } else if (template.slots('n').contains(slot)) {
            openRewardList(player, session, category, holder.page);
        }
    }

    void openRewardList(
            Player player,
            EditorSession session,
            EditorCategory category,
            int requestedPage) {
        GuiTemplate template = context.screens.template("reward-list");
        ConfigurationSection entries = session.yaml.getConfigurationSection("entries");
        List<String> ids = entries == null ? List.of() : entries.getKeys(false).stream()
                .filter(id -> category.acceptsRewardType(entries.getString(id + ".type", "item")))
                .sorted().toList();
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (ids.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(GuiView.REWARD_LIST, AdminType.DROP_GROUP,
                session.id, category.key(), page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "id", session.id,
                "category", context.screens.categoryName(player, category),
                "page", page + 1));
        int start = page * pageSize;
        List<Integer> slots = template.slots('e');
        for (int index = 0; index < slots.size() && start + index < ids.size(); index++) {
            String entryId = ids.get(start + index);
            int slot = slots.get(index);
            String type = entries.getString(entryId + ".type", "item");
            inventory.setItem(slot, rewardListItem(template, player, entries, entryId, type));
            holder.valuesBySlot.put(slot, entryId);
        }
        for (char symbol : new char[]{'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        if (category.acceptsRewardType("item")) {
            context.screens.fillStatic(inventory, template, 'i', player, Map.of());
        }
        if (category.acceptsRewardType("command")) {
            context.screens.fillStatic(inventory, template, 'c', player, Map.of());
        }
        player.openInventory(inventory);
    }

    private void openRewardEditor(
            Player player,
            EditorSession session,
            EditorCategory category,
            String entryId,
            RewardEntryDraft draft) {
        GuiTemplate template = context.screens.template(draft.type().equals("item")
                ? "item-reward-editor" : "command-reward-editor");
        List<String> fields = draft.fields();
        int pageSize = template.slots('f').size();
        int maximumPage = Math.max(0, (fields.size() - 1) / pageSize);
        session.page = Math.max(0, Math.min(maximumPage, session.page));
        GuiHolder holder = new GuiHolder(GuiView.REWARD_EDITOR, AdminType.DROP_GROUP,
                entryId, category.key(), session.page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "id", entryId,
                "type", context.screens.rewardTypeName(player, draft.type())));
        int start = session.page * pageSize;
        List<Integer> fieldSlots = template.slots('f');
        for (int index = 0; index < fieldSlots.size() && start + index < fields.size(); index++) {
            String field = fields.get(start + index);
            int slot = fieldSlots.get(index);
            inventory.setItem(slot, context.screens.editorFieldItem(
                    template, player, field, draft.value(field)));
            holder.valuesBySlot.put(slot, field);
        }
        for (char symbol : new char[]{'p', 'b', 's', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        if (draft.type().equals("item")) {
            for (int slot : template.slots('i')) {
                inventory.setItem(slot, rewardEditorItem(template, player, draft));
            }
        }
        player.openInventory(inventory);
    }

    private void openRewardConfirm(
            Player player,
            EditorSession session,
            EditorCategory category,
            String entryId,
            int page) {
        GuiTemplate template = context.screens.template("confirm");
        GuiHolder holder = new GuiHolder(GuiView.REWARD_CONFIRM, session.type,
                entryId, category.key(), page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of("id", entryId));
        context.screens.fillStatic(inventory, template, 'y', player, Map.of("id", entryId));
        context.screens.fillStatic(inventory, template, 'n', player, Map.of("id", entryId));
        player.openInventory(inventory);
    }

    private void beginCreateReward(
            Player player,
            EditorSession session,
            EditorCategory category,
            String type) {
        context.chatInput.begin(player, context.messages.text(player, "gui.reward-list.input-id"),
                input -> createRewardDraft(player, session, category, type, input),
                () -> openRewardList(player, session, category, 0));
    }

    private void createRewardDraft(
            Player player,
            EditorSession session,
            EditorCategory category,
            String type,
            String input) {
        String entryId = input.trim();
        if (!EditorTargets.SAFE_ID.matcher(entryId).matches()) {
            context.messages.send(player, "common.invalid-id", Map.of());
            openRewardList(player, session, category, 0);
            return;
        }
        if (session.yaml.contains("entries." + entryId)) {
            context.messages.send(player, "gui.reward-list.duplicate-id", Map.of("id", entryId));
            openRewardList(player, session, category, 0);
            return;
        }
        String defaultRarity = context.configuredRarities.get().stream()
                .findFirst()
                .map(Rarity::id)
                .orElseThrow(() -> new IllegalStateException("没有可用的奖励稀有度"));
        RewardEntryDraft draft = RewardEntryDraft.create(type, defaultRarity);
        context.sessions.rewardDrafts.put(player.getUniqueId(), draft);
        session.page = 0;
        openRewardEditor(player, session, category, entryId, draft);
    }

    private void saveReward(
            Player player,
            EditorSession session,
            EditorCategory category,
            String entryId,
            RewardEntryDraft draft) {
        if (draft.type().equals("item") && !draft.hasItem()) {
            context.messages.send(player, "gui.reward-editor.item-required", Map.of());
            openRewardEditor(player, session, category, entryId, draft);
            return;
        }
        try {
            GuiWeightRules.requireValid(draft.weight(), context.safetyLimits);
        } catch (IllegalArgumentException exception) {
            String reason = context.messages.text(player, "gui.mob-member-editor.invalid-weight")
                    .replace("{max}", Long.toString(context.safetyLimits.maxWeight()));
            context.messages.send(player, "input.invalid", Map.of("reason", reason));
            openRewardEditor(player, session, category, entryId, draft);
            return;
        }
        try {
            EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
            draft.applyTo(working.yaml, "entries." + entryId);
            context.sessionService.persistSession(player, working);
            context.sessions.rewardDrafts.remove(player.getUniqueId());
            context.messages.send(player, "common.saved", Map.of());
            context.sessionService.refreshSession(player, session,
                    refreshed -> openRewardList(player, refreshed, category, 0));
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            context.sessionService.refreshAfterFailure(player, session,
                    refreshed -> openRewardEditor(player, refreshed, category, entryId, draft));
        }
    }

    private ItemStack rewardListItem(
            GuiTemplate template,
            Player player,
            ConfigurationSection entries,
            String entryId,
            String type) {
        ItemStack configured = type.equalsIgnoreCase("item") ? entries.getItemStack(entryId + ".item") : null;
        if (configured == null && type.equalsIgnoreCase("item")) {
            Material material = Material.matchMaterial(entries.getString(entryId + ".material", ""));
            if (material != null && !material.isAir()) {
                configured = new ItemStack(material);
            }
        }
        ItemStack guide = template.item('e', player, Map.of(
                "id", entryId, "type", context.screens.rewardTypeName(player, type)));
        if (configured == null) {
            return guide;
        }
        ItemStack item = configured.clone();
        item.setAmount(1);
        var meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.addAll(context.messages.platform().lore(meta));
        lore.addAll(context.messages.platform().lore(guide.getItemMeta()));
        context.messages.platform().lore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack rewardEditorItem(GuiTemplate template, Player player, RewardEntryDraft draft) {
        ItemStack guide = template.item('i', player, Map.of());
        if (!draft.hasItem()) {
            return guide;
        }
        ItemStack item = draft.item();
        var meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.addAll(context.messages.platform().lore(meta));
        lore.addAll(context.messages.platform().lore(guide.getItemMeta()));
        context.messages.platform().lore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rewardRarityItem(GuiTemplate template, Player player, Rarity rarity, boolean selected) {
        ItemStack item = template.item(selected ? 's' : 'u', player, Map.of(
                "rarity", rarity.display(context.playerLocale.apply(player)),
                "id", rarity.id(),
                "priority", rarity.priority()));
        item.setType(rarity.selectorMaterial());
        return item;
    }
}
