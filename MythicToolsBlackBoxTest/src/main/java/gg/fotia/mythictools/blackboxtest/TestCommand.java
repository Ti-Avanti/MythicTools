package gg.fotia.mythictools.blackboxtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** `/mttest` 命令路由。 */
final class TestCommand implements TabExecutor {
    private static final List<String> ROOTS = List.of(
            "snapshot", "reset", "restore", "fixture", "spawn", "kill", "damage",
            "point", "boss", "schedule-kill", "state", "reload", "remove", "pending", "assert",
            "papi", "papi-async", "render", "visual", "gui-visual", "mark", "permission");
    private final TestActions probe;

    TestCommand(TestActions probe) {
        this.probe = probe;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("MTTEST_ERROR reason=missing-subcommand");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("mark")) {
            probe.mark(sender, args.length >= 2 ? args[1] : "default");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("MTTEST_ERROR reason=player-required");
            return true;
        }
        try {
            dispatch(player, sub, args);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("MTTEST_ERROR reason=" + exception.getMessage());
        }
        return true;
    }

    void dispatch(Player player, String sub, String[] args) {
        switch (sub) {
            case "snapshot" -> probe.snapshot(player);
            case "reset" -> probe.reset(player);
            case "restore" -> probe.restore(player);
            case "fixture" -> probe.fixture(player, require(args, 1, "fixture-mode"));
            case "spawn" -> probe.spawn(player, require(args, 1, "mob-id"));
            case "kill" -> probe.kill(player,
                    args.length >= 2 ? args[1] : "player",
                    args.length >= 3 ? args[2] : null);
            case "damage" -> probe.damage(player, Double.parseDouble(require(args, 1, "amount")));
            case "point" -> probe.triggerPoint(player);
            case "boss" -> probe.spawnBoss(player);
            case "schedule-kill" -> probe.scheduleKill(
                    player, Integer.parseInt(require(args, 1, "ticks")));
            case "state" -> probe.state(player);
            case "reload" -> probe.reload(player);
            case "remove" -> probe.remove(player, require(args, 1, "mob-or-boss"));
            case "pending" -> probe.pending(player, Arrays.copyOfRange(args, 1, args.length));
            case "assert" -> probe.assertCase(player, require(args, 1, "assert-case"));
            case "papi" -> probe.placeholder(player, require(args, 1, "params"));
            case "papi-async" -> probe.asyncPlaceholder(player, require(args, 1, "params"));
            case "render" -> probe.render(player, require(args, 1, "fixture"));
            case "visual" -> probe.visualMetadata(player);
            case "gui-visual" -> probe.captureGuiVisualMetadata(player);
            case "permission" -> probe.permission(
                    player, require(args, 1, "node"), require(args, 2, "value"));
            default -> throw new IllegalArgumentException("unknown-subcommand-" + sub);
        }
    }

    private static String require(String[] args, int index, String name) {
        if (args.length <= index || args[index].isBlank()) {
            throw new IllegalArgumentException("missing-" + name);
        }
        return args[index];
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOTS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("fixture")) {
            List<String> modes = new ArrayList<>(probe.fixtures().modes());
            modes.add("restore");
            modes.add("clear");
            return filter(modes, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return filter(List.of("MTQA_DropMob", "MTQA_SpawnMob", "MTQA_BossPhase1"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kill")) {
            return filter(List.of("player", "environment"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return filter(List.of("mob", "boss", "MTQA_SpawnMob", "MTQA_BossPhase1", "MTQA_BossPhase2"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pending")) {
            return filter(List.of("clear", "seed", "corrupt", "inspect"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("assert")) {
            return filter(List.of(
                    "runtime", "rollback", "external-remove", "pending-corrupt", "pending-clear", "chat-cleared"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("permission")) {
            return filter(List.of("true", "false", "clear"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
