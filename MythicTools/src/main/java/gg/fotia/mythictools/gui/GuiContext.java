package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.SafetyLimits;
import gg.fotia.mythictools.reward.Rarity;
import gg.fotia.mythictools.reward.FirstDefeatRewardOption;
import gg.fotia.mythictools.runtime.OwnedTasks;
import gg.fotia.mythictools.text.MessageRenderer;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** 管理 GUI 各控制器共享的依赖与晚绑定引用。 */
final class GuiContext {
    final JavaPlugin plugin;
    final GuiTemplateRepository templates;
    final MessageRenderer messages;
    final ChatInputManager chatInput;
    final Consumer<AdminType> saveReloadAction;
    final Consumer<Player> fullReloadAction;
    final Predicate<String> mobExists;
    final Supplier<List<String>> mobIds;
    final Supplier<List<String>> loadedMobGroupIds;
    final Supplier<List<String>> loadedDropGroupIds;
    final Supplier<List<Rarity>> configuredRarities;
    final Supplier<List<FirstDefeatRewardOption>> firstDefeatRewardOptions;
    final Function<Player, String> playerLocale;
    final SafetyLimits safetyLimits;
    final OwnedTasks tasks;
    final GuiSessions sessions;
    final EditorTargets targets;
    final EditorValueFormats values;
    final GuiScreenSupport screens;
    final EditorSessionService sessionService;
    boolean closed;

    CoreGuiController core;
    EditorSelectorController selector;
    RewardGuiController rewards;
    MobMemberGuiController mobMembers;
    MobDropGroupGuiController mobDropGroups;
    BossPhaseGuiController bossPhases;
    BossRewardGuiController bossRewards;
    BossSpawnerGuiController bossSpawners;
    BossScheduleGuiController bossSchedules;
    FirstDefeatGuiController firstDefeats;

    GuiContext(
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
        this.plugin = plugin;
        this.templates = templates;
        this.messages = messages;
        this.chatInput = chatInput;
        this.saveReloadAction = saveReloadAction;
        this.fullReloadAction = fullReloadAction;
        this.mobExists = mobExists;
        this.mobIds = mobIds;
        this.loadedMobGroupIds = loadedMobGroupIds;
        this.loadedDropGroupIds = loadedDropGroupIds;
        this.configuredRarities = configuredRarities;
        this.firstDefeatRewardOptions = firstDefeatRewardOptions;
        this.playerLocale = playerLocale;
        this.safetyLimits = safetyLimits;
        this.tasks = tasks;
        this.sessions = new GuiSessions();
        this.targets = new EditorTargets(plugin, mobIds, loadedMobGroupIds, loadedDropGroupIds);
        this.values = new EditorValueFormats(messages);
        this.screens = new GuiScreenSupport(templates, messages, values);
        this.sessionService = new EditorSessionService(this);
    }

    /** 构造完成后由 AdminGuiManager 绑定全部控制器。 */
    void bindControllers(
            CoreGuiController core,
            EditorSelectorController selector,
            RewardGuiController rewards,
            MobMemberGuiController mobMembers,
            MobDropGroupGuiController mobDropGroups,
            BossPhaseGuiController bossPhases,
            BossRewardGuiController bossRewards,
            BossSpawnerGuiController bossSpawners,
            BossScheduleGuiController bossSchedules,
            FirstDefeatGuiController firstDefeats) {
        this.core = core;
        this.selector = selector;
        this.rewards = rewards;
        this.mobMembers = mobMembers;
        this.mobDropGroups = mobDropGroups;
        this.bossPhases = bossPhases;
        this.bossRewards = bossRewards;
        this.bossSpawners = bossSpawners;
        this.bossSchedules = bossSchedules;
        this.firstDefeats = firstDefeats;
    }
}
