package gg.fotia.mythictools.blackboxtest;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** `/mttest` 路由与实际 Bukkit 探针之间的窄接口。 */
abstract class TestActions {
    abstract FixtureManager fixtures();
    abstract void snapshot(Player player);
    abstract void reset(Player player);
    abstract void restore(Player player);
    abstract void fixture(Player player, String mode);
    abstract void spawn(Player player, String mobId);
    abstract void kill(Player player, String attribution, String mobId);
    abstract void damage(Player player, double amount);
    abstract void triggerPoint(Player player);
    abstract void spawnBoss(Player player);
    abstract void scheduleKill(Player player, int ticks);
    abstract void state(Player player);
    abstract void reload(Player player);
    abstract void remove(Player player, String target);
    abstract void pending(Player player, String[] args);
    abstract void assertCase(Player player, String testCase);
    abstract void placeholder(Player player, String params);
    abstract void asyncPlaceholder(Player player, String params);
    abstract void render(Player player, String fixture);
    abstract void visualMetadata(Player player);
    abstract void captureGuiVisualMetadata(Player player);
    abstract void mark(CommandSender sender, String key);
    abstract void permission(Player player, String node, String rawValue);
}
