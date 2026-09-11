package gg.fotia.mythictools;

import gg.fotia.mythictools.boss.BossManager;
import gg.fotia.mythictools.boss.BossRepository;
import gg.fotia.mythictools.command.MythicToolsCommand;
import gg.fotia.mythictools.config.ConfigDomain;
import gg.fotia.mythictools.config.ConfigLoadMode;
import gg.fotia.mythictools.config.ConfigLoadReport;
import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.config.ResourceInstaller;
import gg.fotia.mythictools.config.YamlFiles;
import gg.fotia.mythictools.config.ConfigIoService;
import gg.fotia.mythictools.config.ConfigFileSnapshot;
import gg.fotia.mythictools.gui.AdminGuiManager;
import gg.fotia.mythictools.gui.GuiHolder;
import gg.fotia.mythictools.lang.LocaleService;
import gg.fotia.mythictools.lang.PacketLocaleListener;
import gg.fotia.mythictools.papi.MythicToolsExpansion;
import gg.fotia.mythictools.runtime.BukkitTaskScheduler;
import gg.fotia.mythictools.runtime.OwnedTasks;
import gg.fotia.mythictools.runtime.PluginRuntime;
import gg.fotia.mythictools.runtime.RuntimeSwap;
import gg.fotia.mythictools.reward.FirstDefeatDispatcher;
import gg.fotia.mythictools.spawning.SpawningManager;
import gg.fotia.mythictools.spawning.SpawningRepository;
import gg.fotia.mythictools.storage.PendingRewardRepository;
import gg.fotia.mythictools.storage.FirstDefeatRepository;
import gg.fotia.mythictools.text.MessageRenderer;
import gg.fotia.mythictools.version.ServerVersion;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** MythicTools 插件入口与原子运行时生命周期。 */
public final class MythicToolsPlugin extends JavaPlugin {
    private final RuntimeSwap<PluginRuntime> runtimes = new RuntimeSwap<>(
            exception -> getLogger().log(Level.WARNING, "旧运行时清理失败，新运行时继续工作", exception));
    private PendingRewardRepository pendingRewards;
    private FirstDefeatRepository firstDefeats;
    private FirstDefeatDispatcher firstDefeatDispatcher;
    private OwnedTasks infrastructureTasks;
    private File databaseFile;
    private PacketLocaleListener packetLocaleListener;
    private MythicToolsExpansion expansion;
    private ConfigIoService configurationIo;

    @Override
    public void onEnable() {
        long started = System.currentTimeMillis();
        try {
            ResourceInstaller.install(this);
            YamlConfiguration configuration = loadMainConfiguration();
            PluginSettings initialSettings = PluginSettings.load(configuration);
            databaseFile = databaseFile(initialSettings);
            infrastructureTasks = new OwnedTasks(new BukkitTaskScheduler(this));
            configurationIo = new ConfigIoService(getDataFolder(), this::executePersistent);
            pendingRewards = new PendingRewardRepository(this, databaseFile, infrastructureTasks);
            firstDefeats = new FirstDefeatRepository(databaseFile);
            firstDefeatDispatcher = new FirstDefeatDispatcher(
                    firstDefeats, () -> runtime().rewardService(), this::executePersistent, getLogger());
            runtimes.installInitial(PluginRuntime.prepare(
                    this, configuration, ConfigLoadMode.STARTUP_LENIENT, pendingRewards, firstDefeats));
            registerStableBridges();
            infrastructureTasks.repeating(firstDefeatDispatcher::recover, 20L, 100L);
            reloadConfig();
            getLogger().info("MythicTools 已启用，耗时 " + (System.currentTimeMillis() - started) + "ms");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "MythicTools 初始化失败，插件将被禁用", exception);
            shutdownAll();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        shutdownAll();
        getLogger().info("MythicTools 已禁用");
    }

    /** 完整准备候选，成功提交后才释放旧运行时。 */
    public void reloadRuntime() {
        if (configurationIo.busy()) {
            throw new IllegalStateException("已有配置操作进行中，请等待完成");
        }
        reloadRuntimeNow();
    }

    public java.util.concurrent.CompletionStage<Void> reloadRuntimeAsync() {
        return configurationIo.submit(snapshot -> {
            YamlFiles.using(snapshot, () -> {
                reloadRuntimeNow();
                return null;
            });
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        });
    }

    private void reloadRuntimeNow() {
        long started = System.currentTimeMillis();
        try {
            runtime().activity().requireIdle();
            YamlConfiguration configuration = loadMainConfiguration();
            PluginSettings candidateSettings = PluginSettings.load(configuration);
            File candidateDatabase = databaseFile(candidateSettings);
            if (!candidateDatabase.equals(databaseFile)) {
                throw new IllegalStateException(
                        "Database.File 无法热切换；旧运行时与待发奖励仍保持可用，请修改后重启服务器");
            }
            YamlFiles.load(new File(getDataFolder(), "placeholders.yml"));
            runtimes.replace(() -> PluginRuntime.prepare(
                    this, configuration, ConfigLoadMode.STRICT, pendingRewards, firstDefeats));
            getConfig().loadFromString(configuration.saveToString());
            if (expansion != null) {
                expansion.reload();
            }
            closeOpenPluginInventories();
            debug("运行时已原子重载，耗时 " + (System.currentTimeMillis() - started) + "ms");
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("无法读取主配置", exception);
        }
    }

    /** 兼容入口：完整数据、语言与 GUI 重载统一使用候选运行时原子切换。 */
    public void reloadData() {
        reloadRuntime();
    }

    /** GUI 保存后只刷新被编辑域（及其依赖域）的数据仓库。 */
    public void reloadEditedData(ConfigDomain domain) {
        if (configurationIo.busy()) {
            throw new IllegalStateException("已有配置操作进行中，请等待完成");
        }
        long started = System.currentTimeMillis();
        ConfigLoadReport report = runtime().reloadRepositories(
                ConfigLoadMode.STRICT, java.util.EnumSet.of(domain));
        debug("配置域 " + domain + " 已重载，耗时 " + (System.currentTimeMillis() - started)
                + "ms，诊断 " + report.diagnostics().size() + " 条");
    }

    /** 严格准备全部数据配置并一次发布，失败时保留旧快照。 */
    public ConfigLoadReport reloadDataStrict() {
        if (configurationIo.busy()) {
            throw new IllegalStateException("已有配置操作进行中，请等待完成");
        }
        long started = System.currentTimeMillis();
        ConfigLoadReport report = runtime().reloadRepositories(ConfigLoadMode.STRICT);
        if (expansion != null) {
            expansion.reload();
        }
        debug("数据仓库已重载，耗时 " + (System.currentTimeMillis() - started)
                + "ms，诊断 " + report.diagnostics().size() + " 条");
        return report;
    }

    /** Debug 配置开启时输出调试日志。 */
    public void debug(String message) {
        PluginRuntime current = runtimes.currentOrNull();
        if (current != null && current.settings().debug()) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    /** 黑盒辅助插件只读查询：玩家当前是否正等待聊天输入。 */
    public boolean isChatInputAwaiting(UUID playerId) {
        PluginRuntime current = runtimes.currentOrNull();
        return current != null && current.isChatInputAwaiting(playerId);
    }

    public PluginSettings settings() {
        return runtime().settings();
    }

    public ServerVersion serverVersion() {
        return runtime().serverVersion();
    }

    public LocaleService locales() {
        return runtime().locales();
    }

    public MessageRenderer messages() {
        return runtime().messages();
    }

    public SpawningRepository spawningRepository() {
        return runtime().spawningRepository();
    }

    public BossRepository bossRepository() {
        return runtime().bossRepository();
    }

    public SpawningManager spawningManager() {
        return runtime().spawningManager();
    }

    public BossManager bossManager() {
        return runtime().bossManager();
    }

    public AdminGuiManager adminGui() {
        return runtime().adminGui();
    }

    public FirstDefeatDispatcher firstDefeatDispatcher() {
        return firstDefeatDispatcher;
    }

    /** 归属于插件生命周期的持久交付回调，不随配置运行时切换而取消。 */
    public void executePersistent(Runnable command) {
        infrastructureTasks.execute(command);
    }

    public ConfigIoService configurationIo() {
        return configurationIo;
    }

    public void reloadFromGui(org.bukkit.entity.Player player) {
        reloadRuntimeAsync().whenComplete((ignored, failure) -> configurationIo.onMain(() -> {
            if (!player.isOnline()) {
                return null;
            }
            Throwable cause = failure == null ? null : ConfigIoService.cause(failure);
            if (cause instanceof gg.fotia.mythictools.runtime.ActiveRuntimeStateException active) {
                messages().send(player, "command.reload-active", java.util.Map.of(
                        "spawning", active.spawningEntities(), "bosses", active.bossFights()));
            } else if (cause != null) {
                getLogger().log(Level.SEVERE, "重载失败", cause);
                messages().send(player, "command.reload-failed", java.util.Map.of("reason", String.valueOf(cause.getMessage())));
            } else {
                messages().send(player, "command.reload-success", java.util.Map.of());
                adminGui().openMain(player);
            }
            return null;
        }));
    }

    public java.util.concurrent.CompletionStage<ConfigFileSnapshot> saveEditedConfiguration(
            File file, ConfigDomain domain, java.util.function.Function<ConfigFileSnapshot, String> edit) {
        return configurationIo.save(file, snapshot -> {
            String contents = YamlFiles.using(snapshot, () -> edit.apply(snapshot));
            Runnable publish = YamlFiles.using(snapshot.with(file, contents),
                    () -> runtime().prepareRepositorySave(domain));
            return new ConfigIoService.PreparedSave(contents, publish);
        });
    }

    private void registerStableBridges() {
        packetLocaleListener = new PacketLocaleListener(() -> {
            PluginRuntime current = runtimes.currentOrNull();
            return current == null ? null : current.locales();
        });
        packetLocaleListener.register();

        expansion = new MythicToolsExpansion(this);
        if (!expansion.register()) {
            throw new IllegalStateException("PlaceholderAPI 扩展注册失败");
        }

        PluginCommand command = getCommand("mythictools");
        if (command == null) {
            throw new IllegalStateException("plugin.yml 缺少 mythictools 命令");
        }
        MythicToolsCommand executor = new MythicToolsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void shutdownAll() {
        if (configurationIo != null) {
            configurationIo.close();
        }
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "注销 PlaceholderAPI 扩展失败", exception);
            }
            expansion = null;
        }
        if (packetLocaleListener != null) {
            try {
                packetLocaleListener.unregister();
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "注销 PacketEvents 语言监听器失败", exception);
            }
            packetLocaleListener = null;
        }
        if (firstDefeatDispatcher != null) {
            firstDefeatDispatcher.close();
            firstDefeatDispatcher = null;
        }
        runtimes.close();
        if (pendingRewards != null) {
            pendingRewards.close();
            pendingRewards = null;
        }
        if (firstDefeats != null) {
            firstDefeats.close();
            firstDefeats = null;
        }
        if (infrastructureTasks != null) {
            infrastructureTasks.cancelAll();
            infrastructureTasks = null;
        }
    }

    private PluginRuntime runtime() {
        return runtimes.current();
    }

    private YamlConfiguration loadMainConfiguration() throws IOException, InvalidConfigurationException {
        return YamlFiles.load(new File(getDataFolder(), "config.yml"));
    }

    private File databaseFile(PluginSettings settings) throws IOException {
        return new File(getDataFolder(), settings.databaseFile()).getCanonicalFile();
    }

    private void closeOpenPluginInventories() {
        for (var player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder) {
                player.closeInventory();
            }
        }
    }
}
