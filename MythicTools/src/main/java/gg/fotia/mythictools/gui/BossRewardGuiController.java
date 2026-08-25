package gg.fotia.mythictools.gui;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Boss 伤害排名与击杀者奖励的菜单、名次与奖励组编辑。 */
final class BossRewardGuiController {
    private final GuiContext context;
    private final BossRewardRules rules;

    BossRewardGuiController(GuiContext context) {
        this.context = context;
        this.rules = new BossRewardRules(context);
    }

    void openBossRankingRewardMenu(Player player, EditorSession session) {
        GuiTemplate template = context.screens.template("boss-ranking-reward-menu");
        GuiHolder holder = new GuiHolder(GuiView.BOSS_RANKING_REWARD_MENU, AdminType.BOSS, session.id, 0);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", session.id));
        boolean enabled = session.yaml.getBoolean("rewards.damage-ranking.enabled", true);
        boolean chatDisplay = session.yaml.getBoolean("rewards.damage-ranking.chat-display", true);
        int maximum = session.yaml.getInt("rewards.damage-ranking.max-recipients", 3);
        int ranks = BossRewardRules.rankIds(session.yaml).size();
        context.screens.fillStatic(inventory, template, 'e', player,
                Map.of("status", rules.status(player, enabled)));
        context.screens.fillStatic(inventory, template, 'm', player, Map.of("value", maximum));
        context.screens.fillStatic(inventory, template, 'c', player,
                Map.of("status", rules.status(player, chatDisplay)));
        context.screens.fillStatic(inventory, template, 'r', player, Map.of("count", ranks));
        context.screens.fillStatic(inventory, template, 'b', player, Map.of());
        player.openInventory(inventory);
    }

    void handleBossRankingRewardMenu(Player player, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || session.type != AdminType.BOSS) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        GuiTemplate template = context.screens.template("boss-ranking-reward-menu");
        if (template.slots('e').contains(slot)) {
            boolean next = !session.yaml.getBoolean("rewards.damage-ranking.enabled", true);
            saveBossRewardMutation(player, session,
                    yaml -> yaml.set("rewards.damage-ranking.enabled", next),
                    refreshed -> openBossRankingRewardMenu(player, refreshed));
        } else if (template.slots('c').contains(slot)) {
            boolean next = !session.yaml.getBoolean("rewards.damage-ranking.chat-display", true);
            saveBossRewardMutation(player, session,
                    yaml -> yaml.set("rewards.damage-ranking.chat-display", next),
                    refreshed -> openBossRankingRewardMenu(player, refreshed));
        } else if (template.slots('m').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.boss-ranking-reward-menu.maximum-input"), input -> {
                        try {
                            int value = Integer.parseInt(input.trim());
                            if (value < 0 || value > context.safetyLimits.maxBossRecipients()) {
                                throw new IllegalArgumentException();
                            }
                            saveBossRewardMutation(player, session,
                                    yaml -> yaml.set("rewards.damage-ranking.max-recipients", value),
                                    refreshed -> openBossRankingRewardMenu(player, refreshed));
                        } catch (NumberFormatException exception) {
                            context.messages.send(player, "input.invalid",
                                    Map.of("reason", invalidMaximumReason(player)));
                            openBossRankingRewardMenu(player, session);
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player, "input.invalid",
                                    Map.of("reason", invalidMaximumReason(player)));
                            openBossRankingRewardMenu(player, session);
                        }
                    }, () -> openBossRankingRewardMenu(player, session));
        } else if (template.slots('r').contains(slot)) {
            openBossRewardRankList(player, session, 0);
        } else if (template.slots('b').contains(slot)) {
            context.core.openCategory(player, session);
        }
    }

    private String invalidMaximumReason(Player player) {
        return context.messages.text(player, "gui.boss-ranking-reward-menu.invalid-maximum")
                .replace("{max}", Integer.toString(context.safetyLimits.maxBossRecipients()));
    }

    void openBossRewardRankList(Player player, EditorSession session, int requestedPage) {
        GuiTemplate template = context.screens.template("boss-reward-rank-list");
        List<Integer> ranks = BossRewardRules.rankIds(session.yaml);
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (ranks.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(GuiView.BOSS_REWARD_RANK_LIST, AdminType.BOSS, session.id, page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", session.id, "page", page + 1));
        int start = page * pageSize;
        List<Integer> slots = template.slots('e');
        for (int index = 0; index < slots.size() && start + index < ranks.size(); index++) {
            int rank = ranks.get(start + index);
            List<Map<?, ?>> groups = session.yaml.getMapList(BossRewardRules.groupsPath("rank:" + rank));
            int total = BossRewardRules.rawTotal(groups);
            int contentSlot = slots.get(index);
            inventory.setItem(contentSlot, template.item('e', player, Map.of(
                    "rank", rank, "groups", groups.size(), "copies", total)));
            holder.valuesBySlot.put(contentSlot, Integer.toString(rank));
        }
        for (char symbol : new char[]{'p', 'b', 'c', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleBossRewardRankList(Player player, GuiHolder holder, int slot, boolean deleteClick) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || session.type != AdminType.BOSS) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        String rank = holder.valuesBySlot.get(slot);
        if (rank != null) {
            if (deleteClick) {
                openBossRewardRankConfirm(player, session, rank, holder.page);
            } else {
                openBossRewardGroupList(player, session, "rank:" + rank, 0);
            }
            return;
        }
        GuiTemplate template = context.screens.template("boss-reward-rank-list");
        if (template.slots('p').contains(slot)) {
            openBossRewardRankList(player, session, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openBossRewardRankList(player, session, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            openBossRankingRewardMenu(player, session);
        } else if (template.slots('c').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.boss-reward-rank-list.input-rank"), input -> {
                        try {
                            int value = Integer.parseInt(input.trim());
                            if (value <= 0) {
                                throw new NumberFormatException();
                            }
                            String path = "rewards.damage-ranking.ranks." + value;
                            if (session.yaml.contains(path)) {
                                context.messages.send(player, "gui.boss-reward-rank-list.duplicate",
                                        Map.of("rank", value));
                                openBossRewardRankList(player, session, 0);
                                return;
                            }
                            saveBossRewardMutation(player, session, yaml -> yaml.set(path, List.of()),
                                    refreshed -> openBossRewardGroupList(
                                            player, refreshed, "rank:" + value, 0));
                        } catch (NumberFormatException exception) {
                            context.messages.send(player, "input.invalid", Map.of("reason",
                                    context.messages.text(player, "gui.boss-reward-rank-list.invalid-rank")));
                            openBossRewardRankList(player, session, 0);
                        }
                    }, () -> openBossRewardRankList(player, session, holder.page));
        }
    }

    private void openBossRewardRankConfirm(
            Player player, EditorSession session, String rank, int page) {
        GuiTemplate template = context.screens.template("confirm");
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_REWARD_RANK_CONFIRM, AdminType.BOSS, rank, page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of("id", rank));
        context.screens.fillStatic(inventory, template, 'y', player, Map.of("id", rank));
        context.screens.fillStatic(inventory, template, 'n', player, Map.of("id", rank));
        player.openInventory(inventory);
    }

    void handleBossRewardRankConfirm(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || session.type != AdminType.BOSS) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        GuiTemplate template = context.screens.template("confirm");
        if (template.slots('y').contains(slot)) {
            saveBossRewardMutation(player, session,
                    yaml -> yaml.set("rewards.damage-ranking.ranks." + holder.id, null),
                    refreshed -> openBossRewardRankList(player, refreshed, holder.page));
        } else if (template.slots('n').contains(slot)) {
            openBossRewardRankList(player, session, holder.page);
        }
    }

    void openBossRewardGroupList(
            Player player, EditorSession session, String target, int requestedPage) {
        GuiTemplate template = context.screens.template("boss-reward-group-list");
        List<Map<?, ?>> groups = session.yaml.getMapList(BossRewardRules.groupsPath(target));
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (groups.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_REWARD_GROUP_LIST, AdminType.BOSS, session.id, target, page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "id", session.id, "target", rules.targetName(player, target), "page", page + 1));
        int start = page * pageSize;
        List<Integer> slots = template.slots('e');
        for (int index = 0; index < slots.size() && start + index < groups.size(); index++) {
            int sourceIndex = start + index;
            Map<?, ?> raw = groups.get(sourceIndex);
            String group = String.valueOf(raw.get("group"));
            int copies = BossRewardRules.copiesValue(raw.get("copies"));
            int contentSlot = slots.get(index);
            inventory.setItem(contentSlot, template.item('e', player,
                    Map.of("group", group, "copies", copies)));
            holder.valuesBySlot.put(contentSlot, Integer.toString(sourceIndex));
        }
        for (char symbol : new char[]{'p', 'b', 'c', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        if (target.equals("killer")) {
            boolean enabled = session.yaml.getBoolean("rewards.killer.enabled", true);
            context.screens.fillStatic(inventory, template, 't', player,
                    Map.of("status", rules.status(player, enabled)));
        }
        player.openInventory(inventory);
    }

    void handleBossRewardGroupList(Player player, GuiHolder holder, int slot, boolean deleteClick) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || session.type != AdminType.BOSS || holder.context == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        String target = holder.context;
        String indexText = holder.valuesBySlot.get(slot);
        if (indexText != null) {
            int index = Integer.parseInt(indexText);
            BossRewardGroupDraft draft = BossRewardGroupDraft.load(
                    session.yaml, BossRewardRules.groupsPath(target), index);
            if (deleteClick) {
                openBossRewardGroupConfirm(player, session, target, index, draft.groupId(), holder.page);
            } else {
                context.sessions.bossRewardGroupDrafts.put(player.getUniqueId(), draft);
                openBossRewardGroupEditor(player, session, target, draft);
            }
            return;
        }
        GuiTemplate template = context.screens.template("boss-reward-group-list");
        if (template.slots('p').contains(slot)) {
            openBossRewardGroupList(player, session, target, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openBossRewardGroupList(player, session, target, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            context.sessions.bossRewardGroupDrafts.remove(player.getUniqueId());
            if (target.equals("killer")) {
                context.core.openCategory(player, session);
            } else {
                openBossRewardRankList(player, session, 0);
            }
        } else if (template.slots('c').contains(slot)) {
            if (context.loadedDropGroupIds.get().isEmpty()) {
                context.messages.send(player, "gui.boss-reward-group-list.no-available", Map.of());
                openBossRewardGroupList(player, session, target, holder.page);
                return;
            }
            context.sessions.bossRewardGroupDrafts.remove(player.getUniqueId());
            openBossRewardGroupSelector(player, session, target, 0);
        } else if (target.equals("killer") && template.slots('t').contains(slot)) {
            boolean next = !session.yaml.getBoolean("rewards.killer.enabled", true);
            saveBossRewardMutation(player, session, yaml -> yaml.set("rewards.killer.enabled", next),
                    refreshed -> openBossRewardGroupList(player, refreshed, target, holder.page));
        }
    }

    private void openBossRewardGroupEditor(
            Player player, EditorSession session, String target, BossRewardGroupDraft draft) {
        GuiTemplate template = context.screens.template("boss-reward-group-editor");
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_REWARD_GROUP_EDITOR, AdminType.BOSS, session.id, target, 0);
        Map<String, Object> variables = Map.of(
                "target", rules.targetName(player, target),
                "group", draft.groupId(), "copies", draft.copies());
        Inventory inventory = context.screens.createInventory(holder, template, player, variables);
        for (char symbol : new char[]{'g', 'c', 'b', 's'}) {
            context.screens.fillStatic(inventory, template, symbol, player, variables);
        }
        player.openInventory(inventory);
    }

    void handleBossRewardGroupEditor(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        BossRewardGroupDraft draft = context.sessions.bossRewardGroupDrafts.get(player.getUniqueId());
        if (session == null || session.type != AdminType.BOSS || holder.context == null || draft == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        String target = holder.context;
        GuiTemplate template = context.screens.template("boss-reward-group-editor");
        if (template.slots('g').contains(slot)) {
            openBossRewardGroupSelector(player, session, target, 0);
        } else if (template.slots('c').contains(slot)) {
            context.chatInput.begin(player,
                    context.messages.text(player, "gui.boss-reward-group-editor.copies-input"), input -> {
                        try {
                            int value = Integer.parseInt(input.trim());
                            BossRewardSelectionRules.requireCopies(
                                    value, context.safetyLimits.maxBossGroupCopies());
                            draft.copies(value);
                        } catch (IllegalArgumentException exception) {
                            context.messages.send(player, "input.invalid",
                                    Map.of("reason", context.messages.text(
                                            player, "gui.boss-reward-group-editor.invalid-copies")
                                            .replace("{max}", Integer.toString(
                                                    context.safetyLimits.maxBossGroupCopies()))));
                        }
                        openBossRewardGroupEditor(player, session, target, draft);
                    }, () -> openBossRewardGroupEditor(player, session, target, draft));
        } else if (template.slots('b').contains(slot)) {
            context.sessions.bossRewardGroupDrafts.remove(player.getUniqueId());
            openBossRewardGroupList(player, session, target, 0);
        } else if (template.slots('s').contains(slot)) {
            if (!context.loadedDropGroupIds.get().contains(draft.groupId())) {
                context.messages.send(player, "input.invalid", Map.of("reason", context.messages.text(
                        player, "gui.boss-reward-group-editor.missing-group")
                        .replace("{id}", draft.groupId())));
                openBossRewardGroupEditor(player, session, target, draft);
                return;
            }
            saveBossRewardMutation(player, session, yaml -> {
                draft.applyTo(yaml, BossRewardRules.groupsPath(target));
                rules.validateSelections(player, yaml);
            }, refreshed -> {
                context.sessions.bossRewardGroupDrafts.remove(player.getUniqueId());
                openBossRewardGroupList(player, refreshed, target, 0);
            }, refreshed -> openBossRewardGroupEditor(player, refreshed, target, draft));
        }
    }

    private void openBossRewardGroupSelector(
            Player player, EditorSession session, String target, int requestedPage) {
        BossRewardGroupDraft draft = context.sessions.bossRewardGroupDrafts.get(player.getUniqueId());
        List<String> options = context.loadedDropGroupIds.get().stream().distinct().sorted().toList();
        GuiTemplate template = context.screens.template("selector");
        List<Integer> contentSlots = GuiScreenSupport.selectorContentSlots(template);
        int pageSize = contentSlots.size();
        int maximumPage = Math.max(0, (options.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(
                GuiView.BOSS_REWARD_GROUP_SELECTOR, AdminType.BOSS, session.id, target, page);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of(
                "field", context.messages.text(player, "gui.boss-reward-group-editor.selector-field"),
                "page", page + 1));
        int start = page * pageSize;
        for (int index = 0; index < contentSlots.size() && start + index < options.size(); index++) {
            String groupId = options.get(start + index);
            boolean selected = draft != null && draft.groupId().equals(groupId);
            int contentSlot = contentSlots.get(index);
            inventory.setItem(contentSlot, template.item(selected ? 's' : 'u', player,
                    Map.of("option", groupId)));
            holder.valuesBySlot.put(contentSlot, groupId);
        }
        for (char symbol : new char[]{'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleBossRewardGroupSelector(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || session.type != AdminType.BOSS || holder.context == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        String target = holder.context;
        String groupId = holder.valuesBySlot.get(slot);
        if (groupId != null) {
            BossRewardGroupDraft draft = context.sessions.bossRewardGroupDrafts.get(player.getUniqueId());
            if (draft == null) {
                draft = BossRewardGroupDraft.create(groupId);
                context.sessions.bossRewardGroupDrafts.put(player.getUniqueId(), draft);
            } else {
                draft.groupId(groupId);
            }
            openBossRewardGroupEditor(player, session, target, draft);
            return;
        }
        GuiTemplate template = context.screens.template("selector");
        if (template.slots('p').contains(slot)) {
            openBossRewardGroupSelector(player, session, target, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openBossRewardGroupSelector(player, session, target, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            BossRewardGroupDraft draft = context.sessions.bossRewardGroupDrafts.get(player.getUniqueId());
            if (draft == null) {
                openBossRewardGroupList(player, session, target, 0);
            } else {
                openBossRewardGroupEditor(player, session, target, draft);
            }
        }
    }

    private void openBossRewardGroupConfirm(
            Player player, EditorSession session, String target, int index, String groupId, int page) {
        GuiTemplate template = context.screens.template("confirm");
        GuiHolder holder = new GuiHolder(GuiView.BOSS_REWARD_GROUP_CONFIRM,
                AdminType.BOSS, Integer.toString(index), target, page);
        holder.valuesBySlot.put(-1, groupId);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of("id", groupId));
        context.screens.fillStatic(inventory, template, 'y', player, Map.of("id", groupId));
        context.screens.fillStatic(inventory, template, 'n', player, Map.of("id", groupId));
        player.openInventory(inventory);
    }

    void handleBossRewardGroupConfirm(Player player, GuiHolder holder, int slot) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || session.type != AdminType.BOSS || holder.context == null) {
            context.core.openList(player, AdminType.BOSS, 0);
            return;
        }
        GuiTemplate template = context.screens.template("confirm");
        if (template.slots('y').contains(slot)) {
            String target = holder.context;
            saveBossRewardMutation(player, session, yaml -> {
                BossRewardGroupDraft.remove(
                        yaml, BossRewardRules.groupsPath(target), Integer.parseInt(holder.id));
                rules.validateSelections(player, yaml);
            }, refreshed -> openBossRewardGroupList(player, refreshed, target, holder.page));
        } else if (template.slots('n').contains(slot)) {
            openBossRewardGroupList(player, session, holder.context, holder.page);
        }
    }

    private void saveBossRewardMutation(
            Player player,
            EditorSession session,
            Consumer<YamlConfiguration> mutation,
            Consumer<EditorSession> success) {
        saveBossRewardMutation(player, session, mutation, success, success);
    }

    private void saveBossRewardMutation(
            Player player,
            EditorSession session,
            Consumer<YamlConfiguration> mutation,
            Consumer<EditorSession> success,
            Consumer<EditorSession> failure) {
        try {
            EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
            mutation.accept(working.yaml);
            rules.validateSelections(player, working.yaml);
            context.sessionService.persistSession(player, working);
            context.messages.send(player, "common.saved", Map.of());
            context.sessionService.refreshSession(player, session, success);
        } catch (java.io.IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            context.sessionService.refreshAfterFailure(player, session, failure);
        }
    }
}
