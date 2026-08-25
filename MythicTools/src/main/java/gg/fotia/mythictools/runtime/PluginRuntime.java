package gg.fotia.mythictools.runtime;

import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.boss.BossManager;
import gg.fotia.mythictools.boss.BossRepository;
import gg.fotia.mythictools.config.ConfigDiagnostic;
import gg.fotia.mythictools.config.ConfigDomain;
import gg.fotia.mythictools.config.ConfigLoadException;
import gg.fotia.mythictools.config.ConfigLoadMode;
import gg.fotia.mythictools.config.ConfigLoadReport;
import gg.fotia.mythictools.config.ConfigSeverity;
import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.config.RepositoryReloadCoordinator;
import gg.fotia.mythictools.config.RepositorySnapshotStore;
import gg.fotia.mythictools.drops.DropListener;
import gg.fotia.mythictools.gui.AdminGuiManager;
import gg.fotia.mythictools.gui.ChatInputManager;
import gg.fotia.mythictools.gui.ChatInputSessions;
import gg.fotia.mythictools.gui.GuiRuntimeReloadAction;
import gg.fotia.mythictools.gui.GuiTemplateRepository;
import gg.fotia.mythictools.integration.MythicMobGateway;
import gg.fotia.mythictools.integration.MythicMobsAdapter;
import gg.fotia.mythictools.item.ItemFactory;
import gg.fotia.mythictools.lang.LocaleService;
import gg.fotia.mythictools.lang.PlayerLocaleListener;
import gg.fotia.mythictools.platform.PlatformBridge;
import gg.fotia.mythictools.platform.PlatformBridgeFactory;
import gg.fotia.mythictools.platform.ServerPlatform;
import gg.fotia.mythictools.reward.RewardRepository;
import gg.fotia.mythictools.reward.RewardService;
import gg.fotia.mythictools.reward.WeightedRewardSelector;
import gg.fotia.mythictools.spawning.SpawnLocationFinder;
import gg.fotia.mythictools.spawning.SpawningManager;
import gg.fotia.mythictools.spawning.SpawningRepository;
import gg.fotia.mythictools.storage.PendingRewardQueue;
import gg.fotia.mythictools.text.MessageRenderer;
import gg.fotia.mythictools.version.ServerVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/** 一次热重载中整体准备、激活和释放的不可拆分运行时。 */
public final class PluginRuntime implements ManagedRuntime {
    private final MythicToolsPlugin plugin;
    private final PluginSettings settings;
    private final ServerVersion serverVersion;
    private final LocaleService locales;
    private final MessageRenderer messages;
    private final RewardRepository rewardRepository;
    private final SpawningRepository spawningRepository;
    private final BossRepository bossRepository;
    private final RepositoryReloadCoordinator reloadCoordinator;
    private final SpawningManager spawningManager;
    private final BossManager bossManager;
    private final GuiTemplateRepository guiTemplates;
    private final ChatInputManager chatInput;
    private final AdminGuiManager adminGui;
    private final PlayerLocaleListener playerLocaleListener;
    private final DropListener dropListener;
    private final OwnedTasks runtimeTasks;
    private final List<Listener> registeredListeners = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private PluginRuntime(
            MythicToolsPlugin plugin,
            PluginSettings settings,
            ServerVersion serverVersion,
            LocaleService locales,
            MessageRenderer messages,
            RewardRepository rewardRepository,
            SpawningRepository spawningRepository,
            BossRepository bossRepository,
            RepositoryReloadCoordinator reloadCoordinator,
            SpawningManager spawningManager,
            BossManager bossManager,
            GuiTemplateRepository guiTemplates,
            ChatInputManager chatInput,
            AdminGuiManager adminGui,
            PlayerLocaleListener playerLocaleListener,
            DropListener dropListener,
            OwnedTasks runtimeTasks) {
        this.plugin = plugin;
        this.settings = settings;
        this.serverVersion = serverVersion;
        this.locales = locales;
        this.messages = messages;
        this.rewardRepository = rewardRepository;
        this.spawningRepository = spawningRepository;
        this.bossRepository = bossRepository;
        this.reloadCoordinator = reloadCoordinator;
        this.spawningManager = spawningManager;
        this.bossManager = bossManager;
        this.guiTemplates = guiTemplates;
        this.chatInput = chatInput;
        this.adminGui = adminGui;
        this.playerLocaleListener = playerLocaleListener;
        this.dropListener = dropListener;
        this.runtimeTasks = runtimeTasks;
    }

    /** 无外部注册副作用地准备候选；所有配置和语言错误在此阶段暴露。 */
    public static PluginRuntime prepare(
            MythicToolsPlugin plugin,
            FileConfiguration configuration,
            ConfigLoadMode loadMode,
            PendingRewardQueue pendingRewards) {
        long started = System.currentTimeMillis();
        PluginSettings settings = PluginSettings.load(configuration);
        ServerVersion serverVersion = ServerVersion.detect();
        if (!serverVersion.isSupported()) {
            throw new IllegalStateException("不支持的服务端版本: " + Bukkit.getBukkitVersion()
                    + "，支持 1.20.1、1.20.4、全部 1.21.x、26.1.2+ 与全部 26.2.x");
        }

        LocaleService locales = new LocaleService(plugin, settings);
        locales.reload();
        PlatformBridge platform = PlatformBridgeFactory.create(ServerPlatform.detect(), plugin.getLogger());
        MessageRenderer messages = new MessageRenderer(
                locales, settings, Bukkit.getPluginManager(), plugin.getLogger(), platform);
        MythicMobGateway mythicMobs = new MythicMobsAdapter();
        RepositorySnapshotStore snapshotStore = new RepositorySnapshotStore();
        RewardRepository rewardRepository = new RewardRepository(plugin, snapshotStore, settings.safetyLimits());
        SpawningRepository spawningRepository = new SpawningRepository(
                plugin, settings, mythicMobs, snapshotStore);
        BossRepository bossRepository = new BossRepository(
                plugin, settings, mythicMobs, rewardRepository, snapshotStore);
        RepositoryReloadCoordinator reloadCoordinator = new RepositoryReloadCoordinator(
                snapshotStore, rewardRepository, spawningRepository, bossRepository);
        reloadRepositories(plugin, reloadCoordinator, loadMode);

        TaskScheduler scheduler = new BukkitTaskScheduler(plugin);
        OwnedTasks runtimeTasks = new OwnedTasks(scheduler);
        WeightedRewardSelector selector = new WeightedRewardSelector(rewardRepository);
        RewardService rewardService = new RewardService(
                rewardRepository, pendingRewards, locales, messages, settings,
                command -> runtimeTasks.execute(command), plugin.getLogger());
        SpawnLocationFinder locationFinder = new SpawnLocationFinder(settings.spawnLocationAttempts());
        SpawningManager spawningManager = new SpawningManager(
                spawningRepository, mythicMobs, locationFinder, settings.spawnCheckPeriodTicks(),
                settings.overrideSpawning(), new OwnedTasks(scheduler));
        BossManager bossManager = new BossManager(
                plugin, bossRepository, mythicMobs, locationFinder, selector, rewardService,
                locales, messages, settings.overrideBoss(), new OwnedTasks(scheduler));
        PlayerLocaleListener playerLocaleListener = new PlayerLocaleListener(
                locales, pendingRewards, messages, settings.dropOverflowAtFeet(),
                command -> runtimeTasks.execute(command), plugin.getLogger());
        DropListener dropListener = new DropListener(
                mythicMobs, rewardRepository, selector, rewardService, bossManager::dropAction);

        ItemFactory itemFactory = new ItemFactory(messages, serverVersion, plugin.getLogger());
        GuiTemplateRepository guiTemplates = new GuiTemplateRepository(plugin, messages, itemFactory);
        guiTemplates.reload();
        ChatInputManager chatInput = new ChatInputManager(
                plugin, messages, new ChatInputSessions(), new OwnedTasks(scheduler));
        AdminGuiManager adminGui = new AdminGuiManager(
                plugin, guiTemplates, messages, chatInput, type -> plugin.reloadEditedData(type.domain()),
                new GuiRuntimeReloadAction(
                        plugin::reloadRuntime,
                        player -> plugin.messages().send(player, "command.reload-success", Map.of()),
                        player -> plugin.adminGui().openMain(player)),
                mythicMobs::exists, mythicMobs::mobIds,
                () -> List.copyOf(spawningRepository.mobGroupIds()),
                () -> List.copyOf(rewardRepository.groupIds()),
                rewardRepository::rarities, locales::locale, settings.safetyLimits(),
                new OwnedTasks(scheduler));

        if (settings.debug()) {
            plugin.getLogger().info("[DEBUG] 运行时候选准备完成，耗时 "
                    + (System.currentTimeMillis() - started) + "ms");
        }
        return new PluginRuntime(
                plugin, settings, serverVersion, locales, messages, rewardRepository,
                spawningRepository, bossRepository, reloadCoordinator, spawningManager, bossManager,
                guiTemplates, chatInput, adminGui, playerLocaleListener, dropListener, runtimeTasks);
    }

    @Override
    public void activate() {
        register(playerLocaleListener);
        if (settings.overrideSpawning()) {
            register(spawningManager);
            spawningManager.start();
        }
        if (settings.overrideBoss()) {
            register(bossManager);
            bossManager.start();
        }
        if (settings.overrideDrops()) {
            register(dropListener);
        }
        register(chatInput);
        register(adminGui);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        RuntimeException failure = null;
        try {
            adminGui.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            bossManager.stop(true);
        } catch (RuntimeException exception) {
            failure = combine(failure, exception);
        }
        try {
            spawningManager.stop();
        } catch (RuntimeException exception) {
            failure = combine(failure, exception);
        }
        for (Listener listener : List.copyOf(registeredListeners)) {
            try {
                HandlerList.unregisterAll(listener);
            } catch (RuntimeException exception) {
                failure = combine(failure, exception);
            }
        }
        registeredListeners.clear();
        runtimeTasks.cancelAll();
        if (failure != null) {
            throw failure;
        }
    }

    public ConfigLoadReport reloadRepositories(ConfigLoadMode mode) {
        return reloadRepositories(plugin, reloadCoordinator, mode);
    }

    /** 只重载指定配置域（连带其依赖域），未变更域复用当前快照。 */
    public ConfigLoadReport reloadRepositories(ConfigLoadMode mode, java.util.Set<ConfigDomain> domains) {
        try {
            ConfigLoadReport report = reloadCoordinator.reload(mode, domains);
            logConfigReport(plugin, report);
            return report;
        } catch (ConfigLoadException exception) {
            logConfigReport(plugin, exception.report());
            throw exception;
        }
    }

    public PluginSettings settings() {
        return settings;
    }

    public ServerVersion serverVersion() {
        return serverVersion;
    }

    public LocaleService locales() {
        return locales;
    }

    public MessageRenderer messages() {
        return messages;
    }

    public RewardRepository rewardRepository() {
        return rewardRepository;
    }

    public SpawningRepository spawningRepository() {
        return spawningRepository;
    }

    public BossRepository bossRepository() {
        return bossRepository;
    }

    public SpawningManager spawningManager() {
        return spawningManager;
    }

    public BossManager bossManager() {
        return bossManager;
    }

    /** 返回会在完整运行时切换中被清理的活动状态。 */
    public RuntimeActivity activity() {
        return new RuntimeActivity(
                spawningManager.trackedEntityCount(),
                bossManager.activeFightCount());
    }

    public GuiTemplateRepository guiTemplates() {
        return guiTemplates;
    }

    public AdminGuiManager adminGui() {
        return adminGui;
    }

    public boolean isChatInputAwaiting(UUID playerId) {
        return chatInput.isAwaiting(playerId);
    }

    private void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    private static ConfigLoadReport reloadRepositories(
            MythicToolsPlugin plugin,
            RepositoryReloadCoordinator coordinator,
            ConfigLoadMode mode) {
        try {
            ConfigLoadReport report = coordinator.reload(mode);
            logConfigReport(plugin, report);
            return report;
        } catch (ConfigLoadException exception) {
            logConfigReport(plugin, exception.report());
            throw exception;
        }
    }

    private static void logConfigReport(MythicToolsPlugin plugin, ConfigLoadReport report) {
        for (ConfigDiagnostic diagnostic : report.diagnostics()) {
            Level level = diagnostic.severity() == ConfigSeverity.WARNING ? Level.WARNING : Level.SEVERE;
            plugin.getLogger().log(
                    level,
                    diagnostic.sourcePath() + "#" + diagnostic.yamlPath() + " ["
                            + diagnostic.problemCode() + "] " + diagnostic.message(),
                    diagnostic.cause());
        }
    }

    private static RuntimeException combine(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}
