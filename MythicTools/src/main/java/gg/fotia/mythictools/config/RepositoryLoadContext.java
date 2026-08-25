package gg.fotia.mythictools.config;

import gg.fotia.mythictools.integration.MythicMobGateway;
import java.io.File;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/** 仓库解析所需的最小外部环境；让候选解析无需启动完整 Paper 服务端。 */
public record RepositoryLoadContext(
        File dataFolder,
        Logger logger,
        Predicate<String> mythicMobExists,
        Function<String, World> worldResolver) {

    public RepositoryLoadContext {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(mythicMobExists, "mythicMobExists");
        Objects.requireNonNull(worldResolver, "worldResolver");
    }

    public static RepositoryLoadContext runtime(JavaPlugin plugin, MythicMobGateway mythicMobs) {
        return new RepositoryLoadContext(
                plugin.getDataFolder(), plugin.getLogger(), mythicMobs::exists, Bukkit::getWorld);
    }

    public static RepositoryLoadContext rewards(JavaPlugin plugin) {
        return new RepositoryLoadContext(
                plugin.getDataFolder(), plugin.getLogger(), ignored -> false, ignored -> null);
    }
}
