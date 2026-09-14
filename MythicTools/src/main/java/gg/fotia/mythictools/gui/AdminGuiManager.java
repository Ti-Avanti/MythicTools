package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.SafetyLimits;
import gg.fotia.mythictools.reward.Rarity;
import gg.fotia.mythictools.reward.FirstDefeatRewardOption;
import gg.fotia.mythictools.runtime.BukkitTaskScheduler;
import gg.fotia.mythictools.runtime.OwnedTasks;
import gg.fotia.mythictools.text.MessageRenderer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 布局驱动管理界面的事件入口，按页面类型分发到各领域控制器。 */
public final class AdminGuiManager implements Listener {
    private final GuiContext context;

    public AdminGuiManager(
            JavaPlugin plugin,
            GuiTemplateRepository templates,
            MessageRenderer messages,
            ChatInputManager chatInput,
            Consumer<AdminType> saveReloadAction,
            Consumer<Player> fullReloadAction,
            Predicate<String> mobExists,
            Supplier<List<String>> mobIds,
            Supplier<List<String>> loadedMobGroupIds,
            Supplier<List<String>> loadedDropGroupIds,
            Supplier<List<Rarity>> configuredRarities,
            Supplier<List<FirstDefeatRewardOption>> firstDefeatRewardOptions,
            Function<Player, String> playerLocale,
            SafetyLimits safetyLimits) {
        this(plugin, templates, messages, chatInput, saveReloadAction, fullReloadAction,
                mobExists, mobIds, loadedMobGroupIds, loadedDropGroupIds,
                configuredRarities, firstDefeatRewardOptions, playerLocale, safetyLimits,
                new OwnedTasks(new BukkitTaskScheduler(plugin)));
    }

    public AdminGuiManager(
            JavaPlugin plugin,
            GuiTemplateRepository templates,
            MessageRenderer messages,
            ChatInputManager chatInput,
            Consumer<AdminType> saveReloadAction,
            Consumer<Player> fullReloadAction,
            Predicate<String> mobExists,
            Supplier<List<String>> mobIds,
            Supplier<List<String>> loadedMobGroupIds,
            Supplier<List<String>> loadedDropGroupIds,
            Supplier<List<Rarity>> configuredRarities,
            Supplier<List<FirstDefeatRewardOption>> firstDefeatRewardOptions,
            Function<Player, String> playerLocale,
            SafetyLimits safetyLimits,
            OwnedTasks tasks) {
        this.context = new GuiContext(
                plugin, templates, messages, chatInput, saveReloadAction, fullReloadAction,
                mobExists, mobIds, loadedMobGroupIds, loadedDropGroupIds,
                configuredRarities, firstDefeatRewardOptions, playerLocale,
                java.util.Objects.requireNonNull(safetyLimits, "safetyLimits"),
                java.util.Objects.requireNonNull(tasks, "tasks"));
        context.bindControllers(
                new CoreGuiController(context),
                new EditorSelectorController(context),
                new RewardGuiController(context),
                new MobMemberGuiController(context),
                new MobDropGroupGuiController(context),
                new BossPhaseGuiController(context),
                new BossRewardGuiController(context),
                new BossSpawnerGuiController(context),
                new BossScheduleGuiController(context),
                new FirstDefeatGuiController(context));
    }

    /** 打开管理总览。 */
    public void openMain(Player player) {
        context.core.openMain(player);
    }

    public void openLevelingMenu(Player player) {
        context.leveling.openMenu(player);
    }

    /** 打开某类配置的分页列表。 */
    public void openList(Player player, AdminType type, int requestedPage) {
        context.core.openList(player, type, requestedPage);
    }

    /** 打开指定配置的通用字段编辑器。 */
    public void openEditor(Player player, AdminType type, String id) {
        context.core.openEditor(player, type, id);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();
        event.setCancelled(true);
        if (context.plugin instanceof gg.fotia.mythictools.MythicToolsPlugin plugin
                && (plugin.configurationIo().busy() || context.sessionService.isSaving(player.getUniqueId()))) {
            context.messages.send(player, "common.saving", Map.of());
            return;
        }
        RewardEntryDraft rewardDraft = context.sessions.rewardDrafts.get(player.getUniqueId());
        if (holder.view == GuiView.REWARD_EDITOR && rewardDraft != null
                && rewardDraft.type().equals("item") && rawSlot >= holder.getInventory().getSize()) {
            if (!event.isShiftClick() && event.getClick() != ClickType.DOUBLE_CLICK) {
                event.setCancelled(false);
            }
            return;
        }
        if (rawSlot < 0 || rawSlot >= holder.getInventory().getSize()) {
            return;
        }
        boolean deleteClick = event.isShiftClick() && event.isRightClick();
        switch (holder.view) {
            case MAIN -> context.core.handleMain(player, rawSlot);
            case DROPS_MENU -> context.core.handleDropsMenu(player, rawSlot);
            case SPAWNING_MENU -> context.core.handleSpawningMenu(player, rawSlot);
            case LEVELING_MENU -> context.leveling.handleMenu(player, rawSlot);
            case LIST -> context.core.handleList(player, holder, rawSlot, deleteClick);
            case CATEGORY -> context.core.handleCategory(player, holder, rawSlot);
            case EDITOR, SECTION_EDITOR -> context.core.handleEditor(player, holder, rawSlot);
            case REWARD_LIST -> context.rewards.handleRewardList(player, holder, rawSlot, deleteClick);
            case REWARD_EDITOR -> context.rewards.handleRewardEditor(player, holder, rawSlot);
            case REWARD_RARITY_SELECTOR -> context.rewards.handleRewardRaritySelector(player, holder, rawSlot);
            case REWARD_OPTION_SELECTOR -> context.rewards.handleRewardOptionSelector(player, holder, rawSlot);
            case REWARD_CONFIRM -> context.rewards.handleRewardConfirm(player, holder, rawSlot);
            case FIRST_DEFEAT_MENU -> context.firstDefeats.handleMenu(player, holder, rawSlot);
            case FIRST_DEFEAT_REWARD_LIST -> context.firstDefeats.handleRewardList(
                    player, holder, rawSlot, deleteClick);
            case FIRST_DEFEAT_REWARD_SELECTOR -> context.firstDefeats.handleRewardSelector(
                    player, holder, rawSlot);
            case MOB_MEMBER_LIST -> context.mobMembers.handleMobMemberList(player, holder, rawSlot, deleteClick);
            case MOB_MEMBER_EDITOR -> context.mobMembers.handleMobMemberEditor(player, holder, rawSlot);
            case MOB_MEMBER_MOB_SELECTOR -> context.mobMembers.handleMobMemberMobSelector(player, holder, rawSlot);
            case MOB_MEMBER_CONFIRM -> context.mobMembers.handleMobMemberConfirm(player, holder, rawSlot);
            case MOB_DROP_GROUP_LIST -> context.mobDropGroups.handleMobDropGroupList(
                    player, holder, rawSlot, deleteClick);
            case MOB_DROP_GROUP_EDITOR -> context.mobDropGroups.handleMobDropGroupEditor(player, holder, rawSlot);
            case MOB_DROP_GROUP_SELECTOR -> context.mobDropGroups.handleMobDropGroupSelector(
                    player, holder, rawSlot);
            case MOB_DROP_GROUP_CONFIRM -> context.mobDropGroups.handleMobDropGroupConfirm(player, holder, rawSlot);
            case BOSS_PHASE_LIST -> context.bossPhases.handleBossPhaseList(player, holder, rawSlot, deleteClick);
            case BOSS_PHASE_EDITOR -> context.bossPhases.handleBossPhaseEditor(player, holder, rawSlot);
            case BOSS_PHASE_MOB_SELECTOR -> context.bossPhases.handleBossPhaseMobSelector(player, holder, rawSlot);
            case BOSS_PHASE_CONFIRM -> context.bossPhases.handleBossPhaseConfirm(player, holder, rawSlot);
            case BOSS_RANKING_REWARD_MENU -> context.bossRewards.handleBossRankingRewardMenu(player, rawSlot);
            case BOSS_REWARD_RANK_LIST -> context.bossRewards.handleBossRewardRankList(
                    player, holder, rawSlot, deleteClick);
            case BOSS_REWARD_RANK_CONFIRM -> context.bossRewards.handleBossRewardRankConfirm(player, holder, rawSlot);
            case BOSS_REWARD_GROUP_LIST -> context.bossRewards.handleBossRewardGroupList(
                    player, holder, rawSlot, deleteClick);
            case BOSS_REWARD_GROUP_EDITOR -> context.bossRewards.handleBossRewardGroupEditor(player, holder, rawSlot);
            case BOSS_REWARD_GROUP_SELECTOR -> context.bossRewards.handleBossRewardGroupSelector(
                    player, holder, rawSlot);
            case BOSS_REWARD_GROUP_CONFIRM -> context.bossRewards.handleBossRewardGroupConfirm(
                    player, holder, rawSlot);
            case BOSS_POINT_SCHEDULE_LIST -> context.bossSchedules.handleBossPointScheduleList(
                    player, holder, rawSlot);
            case BOSS_TIME_WINDOW_LIST -> context.bossSchedules.handleBossTimeWindowList(
                    player, holder, rawSlot, deleteClick);
            case BOSS_TIME_WINDOW_EDITOR -> context.bossSchedules.handleBossTimeWindowEditor(player, holder, rawSlot);
            case BOSS_TIME_WINDOW_CONFIRM -> context.bossSchedules.handleBossTimeWindowConfirm(
                    player, holder, rawSlot);
            case BOSS_SPAWNER_LIST -> context.bossSpawners.handleBossSpawnerList(
                    player, holder, rawSlot, deleteClick);
            case BOSS_SPAWNER_EDITOR -> context.bossSpawners.handleBossSpawnerEditor(player, holder, rawSlot);
            case BOSS_SPAWNER_CONFIRM -> context.bossSpawners.handleBossSpawnerConfirm(player, holder, rawSlot);
            case SELECTOR -> context.selector.handleSelector(player, holder, rawSlot);
            case CONFIRM -> context.core.handleConfirm(player, holder, rawSlot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        context.tasks.execute(() -> {
            if (!player.isOnline() || context.chatInput.isAwaiting(playerId)
                    || player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder) {
                return;
            }
            context.sessionService.cleanupSession(playerId);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        context.sessionService.resetEditingState(event.getPlayer().getUniqueId());
    }

    /** 丢弃所有编辑和聊天状态，不触发任何重开或取消回调。 */
    public void close() {
        context.closed = true;
        for (UUID playerId : List.copyOf(context.sessions.editors.keySet())) {
            context.sessionService.cleanupSession(playerId);
        }
        context.sessions.clearAllDrafts();
        context.chatInput.close();
        context.tasks.cancelAll();
    }
}
