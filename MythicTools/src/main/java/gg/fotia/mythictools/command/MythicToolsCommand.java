package gg.fotia.mythictools.command;

import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.runtime.ActiveRuntimeStateException;
import gg.fotia.mythictools.text.MessageRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** `/mythictools` 主命令。 */
public final class MythicToolsCommand implements TabExecutor {
    private final MythicToolsPlugin plugin;

    public MythicToolsCommand(MythicToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        String subCommand = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "gui" -> openGui(sender);
            case "reload" -> reload(sender);
            case "info" -> info(sender);
            case "boss" -> boss(sender, args);
            case "spawnpoint" -> spawnPoint(sender, args);
            default -> unknown(sender);
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("gui", "reload", "info", "boss", "spawnpoint"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("boss")) {
            return filter(List.of("spawn"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("boss")
                && args[1].equalsIgnoreCase("spawn")) {
            return filter(new ArrayList<>(plugin.bossRepository().bossIds()), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawnpoint")) {
            return filter(List.of("trigger"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spawnpoint")
                && args[1].equalsIgnoreCase("trigger")) {
            return filter(new ArrayList<>(plugin.spawningRepository().spawnPointIds()), args[2]);
        }
        return List.of();
    }

    private boolean openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return playerOnly(sender);
        }
        if (!sender.hasPermission("mythictools.admin")) {
            plugin.messages().send(player, "common.no-permission", Map.of());
            return true;
        }
        plugin.adminGui().openMain(player);
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("mythictools.reload")) {
            send(sender, "common.no-permission", Map.of());
            return true;
        }
        try {
            plugin.reloadRuntime();
            send(sender, "command.reload-success", Map.of());
        } catch (ActiveRuntimeStateException exception) {
            send(sender, "command.reload-active", Map.of(
                    "spawning", exception.spawningEntities(),
                    "bosses", exception.bossFights()));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "重载失败", exception);
            send(sender, "command.reload-failed", Map.of("reason", exception.getMessage()));
        }
        return true;
    }

    private boolean info(CommandSender sender) {
        PluginSettings settings = plugin.settings();
        Map<String, Object> values = Map.of(
                "version", plugin.getDescription().getVersion(),
                "server", plugin.getServer().getBukkitVersion(),
                "enabled", "<!i><green>true");
        send(sender, "command.info.header", values);
        send(sender, "command.info.version", values);
        send(sender, "command.info.server", values);
        send(sender, "command.info.drops", Map.of("enabled", coloredBoolean(settings.overrideDrops())));
        send(sender, "command.info.spawning", Map.of("enabled", coloredBoolean(settings.overrideSpawning())));
        send(sender, "command.info.boss", Map.of("enabled", coloredBoolean(settings.overrideBoss())));
        send(sender, "command.info.footer", values);
        return true;
    }

    private boolean boss(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return playerOnly(sender);
        }
        if (!sender.hasPermission("mythictools.admin")) {
            send(sender, "common.no-permission", Map.of());
            return true;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("spawn")) {
            boolean spawned = plugin.bossManager().spawnNow(args[2], player.getLocation());
            send(sender, spawned ? "common.saved" : "common.not-found", Map.of("id", args[2]));
            return true;
        }
        return unknown(sender);
    }

    private boolean spawnPoint(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mythictools.admin")) {
            send(sender, "common.no-permission", Map.of());
            return true;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("trigger")) {
            boolean triggered = plugin.spawningManager().triggerPoint(args[2]);
            send(sender, triggered ? "common.saved" : "common.not-found", Map.of("id", args[2]));
            return true;
        }
        return unknown(sender);
    }

    private boolean unknown(CommandSender sender) {
        send(sender, "common.unknown-command", Map.of());
        return true;
    }

    private boolean playerOnly(CommandSender sender) {
        send(sender, "common.player-only", Map.of());
        return true;
    }

    private void send(CommandSender sender, String key, Map<String, ?> variables) {
        MessageRenderer renderer = plugin.messages();
        if (sender instanceof Player player) {
            renderer.send(player, renderer.renderKey(player, key, variables));
        } else {
            renderer.send(sender, renderer.renderKey(plugin.settings().defaultLocale(), key, null, variables));
        }
    }

    private static String coloredBoolean(boolean value) {
        return value ? "<!i><green>true" : "<!i><red>false";
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
