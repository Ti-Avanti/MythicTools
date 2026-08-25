package gg.fotia.mythictools.runtime;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit 调度器适配器。 */
public final class BukkitTaskScheduler implements TaskScheduler {
    private final JavaPlugin plugin;

    public BukkitTaskScheduler(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public TaskHandle execute(Runnable command) {
        var task = Bukkit.getScheduler().runTask(plugin, command);
        return task::cancel;
    }

    @Override
    public TaskHandle later(Runnable command, long delayTicks) {
        var task = Bukkit.getScheduler().runTaskLater(plugin, command, delayTicks);
        return task::cancel;
    }

    @Override
    public TaskHandle repeating(Runnable command, long delayTicks, long periodTicks) {
        var task = Bukkit.getScheduler().runTaskTimer(plugin, command, delayTicks, periodTicks);
        return task::cancel;
    }
}
