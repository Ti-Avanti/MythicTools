package gg.fotia.mythictools.blackboxtest;

import gg.fotia.mythictools.MythicToolsPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

/** MythicTools 全功能 BlackBoxPro 验收辅助插件。 */
public final class MythicToolsBlackBoxTestPlugin extends JavaPlugin {
    private final Map<String, Integer> markers = new HashMap<>();
    private final Map<UUID, PermissionAttachment> permissions = new HashMap<>();
    private MythicToolsPlugin target;
    private TestMessages messages;
    private FixtureManager fixtures;
    private TestProbe probe;

    @Override
    public void onEnable() {
        target = (MythicToolsPlugin) Bukkit.getPluginManager().getPlugin("MythicTools");
        if (target == null || !target.isEnabled()) {
            throw new IllegalStateException("MythicTools 未启用");
        }
        messages = new TestMessages(this);
        messages.load();
        fixtures = new FixtureManager(this, target, messages);
        probe = new TestProbe(this, target, fixtures, messages, markers, permissions);
        PluginCommand command = getCommand("mttest");
        if (command == null) {
            throw new IllegalStateException("plugin.yml 缺少 mttest 命令");
        }
        TestCommand executor = new TestCommand(probe);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getLogger().info("MythicTools BlackBox 测试辅助插件已启用");
    }

    @Override
    public void onDisable() {
        permissions.values().forEach(PermissionAttachment::remove);
        permissions.clear();
    }
}
