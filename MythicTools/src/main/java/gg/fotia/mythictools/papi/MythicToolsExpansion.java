package gg.fotia.mythictools.papi;

import gg.fotia.mythictools.MythicToolsPlugin;
import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** MythicTools 的可编辑 PlaceholderAPI 扩展。 */
public final class MythicToolsExpansion extends PlaceholderExpansion {
    private static final ThreadLocal<DecimalFormat> NUMBER =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.##"));
    private static final int CACHE_LIMIT = 1024;
    private final MythicToolsPlugin plugin;
    /** 主线程计算结果的旁路缓存，供异步轮询方读取，避免跨线程访问非线程安全状态。 */
    private final AsyncRefreshCache<CacheKey, String> asyncCache = new AsyncRefreshCache<>(CACHE_LIMIT);
    private volatile Map<String, String> outputs = Map.of();

    public MythicToolsExpansion(MythicToolsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** 重载 PAPI 输出模板并扁平化为单层查找表。 */
    public void reload() {
        YamlConfiguration yaml;
        try {
            yaml = gg.fotia.mythictools.config.YamlFiles.load(new File(plugin.getDataFolder(), "placeholders.yml"));
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalStateException("无法读取占位符配置", exception);
        }
        Map<String, String> flattened = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            String value = yaml.getString(key);
            if (value != null && !yaml.isConfigurationSection(key)) {
                flattened.put(key, value);
            }
        }
        outputs = Map.copyOf(flattened);
        asyncCache.configure(plugin.settings().performance().placeholderRefreshMillis());
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mythictools";
    }

    @Override
    public @NotNull String getAuthor() {
        return "fotia";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        CacheKey cacheKey = new CacheKey(player == null ? null : player.getUniqueId(), params);
        if (!Bukkit.isPrimaryThread()) {
            return asyncCache.getOrSchedule(
                    cacheKey,
                    task -> Bukkit.getScheduler().runTask(plugin, task),
                    () -> compute(
                            cacheKey.playerId() == null ? null : Bukkit.getPlayer(cacheKey.playerId()),
                            cacheKey.params()));
        }
        String rendered = compute(player, params);
        if (rendered != null) {
            asyncCache.put(cacheKey, rendered);
        }
        return rendered;
    }

    private @Nullable String compute(Player player, String params) {
        String result;
        if (params.equalsIgnoreCase("language")) {
            result = player == null ? plugin.settings().defaultLocale() : plugin.locales().locale(player);
        } else {
            String[] parts = params.split(":");
            if (parts.length == 2 && parts[0].equalsIgnoreCase("override")) {
                result = bool(switch (parts[1].toLowerCase(Locale.ROOT)) {
                case "drops" -> plugin.settings().overrideDrops();
                case "spawning" -> plugin.settings().overrideSpawning();
                case "boss" -> plugin.settings().overrideBoss();
                default -> false;
            });
            } else if (parts.length == 3 && parts[0].equalsIgnoreCase("spawnpoint")) {
                result = spawnPoint(parts[1], parts[2]);
            } else if (parts.length == 3 && parts[0].equalsIgnoreCase("boss")) {
                result = boss(parts[1], parts[2]);
            } else if (parts.length == 4 && parts[0].equalsIgnoreCase("boss-point")
                    && parts[3].equalsIgnoreCase("next")) {
                long seconds = plugin.bossManager().secondsUntilNext(parts[1], parts[2]);
                result = seconds < 0 ? output("not-found") : template("seconds", "value", seconds);
            } else {
                return null;
            }
        }
        return LegacyComponentSerializer.legacySection().serialize(
                plugin.messages().render(result, player, Map.of()));
    }

    private String spawnPoint(String id, String field) {
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "enabled" -> bool(plugin.spawningManager().isPointEnabled(id));
            case "alive" -> String.valueOf(plugin.spawningManager().aliveAtPoint(id));
            case "next" -> {
                long seconds = plugin.spawningManager().secondsUntilNext(id);
                yield seconds < 0 ? output("not-found") : template("seconds", "value", seconds);
            }
            default -> output("unknown");
        };
    }

    private String boss(String id, String field) {
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "alive" -> plugin.bossManager().aliveCount(id) == 0
                    ? output("boss.inactive") : output("boss.alive");
            case "count" -> String.valueOf(plugin.bossManager().aliveCount(id));
            case "phase" -> template("boss.phase",
                    "current", plugin.bossManager().currentPhase(id),
                    "total", plugin.bossManager().totalPhases(id));
            case "stance" -> {
                String stance = plugin.bossManager().currentNativeStance(id);
                yield stance.isBlank() ? output("boss.inactive") : stance;
            }
            case "health" -> template("boss.health",
                    "current", NUMBER.get().format(plugin.bossManager().currentHealth(id)),
                    "max", NUMBER.get().format(plugin.bossManager().maximumHealth(id)));
            case "top-damage" -> plugin.bossManager().topDamage(id)
                    .map(entry -> template("boss.top-damage", "player", entry.getKey(),
                            "damage", NUMBER.get().format(entry.getValue())))
                    .orElse(output("boss.inactive"));
            default -> output("unknown");
        };
    }

    private String bool(boolean value) {
        return output("boolean." + value);
    }

    private String template(String path, Object... values) {
        String result = output(path);
        for (int index = 0; index + 1 < values.length; index += 2) {
            result = result.replace("{" + values[index] + "}", String.valueOf(values[index + 1]));
        }
        return result;
    }

    private String output(String path) {
        return outputs.getOrDefault(path, "");
    }

    private record CacheKey(UUID playerId, String params) {
    }
}
