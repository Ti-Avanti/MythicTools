package gg.fotia.mythictools.lang;

import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.config.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** 按客户端 Locale 选择语言文件并提供文本查询。 */
public final class LocaleService {
    private static final int RESOLVED_CACHE_LIMIT = 512;
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    /** 语言文件在加载期扁平化为单层查找表，消除每条消息的 YAML 路径遍历。 */
    private final Map<String, Map<String, String>> texts = new HashMap<>();
    private final Map<String, String> normalizedIds = new HashMap<>();
    private final Map<String, String> resolvedCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> clientLocales = new ConcurrentHashMap<>();

    public LocaleService(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /** 重载 lang 目录下全部 YAML 语言文件。 */
    public void reload() {
        Map<String, Map<String, String>> loaded = new HashMap<>();
        File directory = new File(plugin.getDataFolder(), "lang");
        File[] files = YamlFiles.list(directory);
        if (files != null) {
            for (File file : files) {
                try {
                    String locale = file.getName().substring(0, file.getName().length() - 4);
                    loaded.put(locale, flatten(YamlFiles.load(file)));
                } catch (IOException | InvalidConfigurationException exception) {
                    plugin.getLogger().log(Level.SEVERE, "无法加载语言文件 " + file.getAbsolutePath(), exception);
                }
            }
        }
        if (!loaded.containsKey(settings.defaultLocale())) {
            throw new IllegalStateException("默认语言文件不存在: " + settings.defaultLocale());
        }
        texts.clear();
        texts.putAll(loaded);
        normalizedIds.clear();
        loaded.keySet().forEach(id -> normalizedIds.put(PluginSettings.normalizeLocale(id), id));
        resolvedCache.clear();
    }

    private static Map<String, String> flatten(YamlConfiguration yaml) {
        Map<String, String> flat = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(key)) {
                String value = yaml.getString(key);
                if (value != null) {
                    flat.put(key, value);
                }
            }
        }
        return Map.copyOf(flat);
    }

    /** 更新 PacketEvents 捕获到的客户端语言。 */
    public void updateClientLocale(UUID playerId, String locale) {
        clientLocales.put(playerId, locale == null ? "" : locale);
    }

    /** 玩家退出时释放语言缓存。 */
    public void remove(UUID playerId) {
        clientLocales.remove(playerId);
    }

    /** 返回玩家当前解析后的语言文件 ID。 */
    public String locale(Player player) {
        if (!settings.followClientLocale() || player == null) {
            return settings.defaultLocale();
        }
        String raw = clientLocales.get(player.getUniqueId());
        if (raw == null || raw.isBlank()) {
            raw = player.getLocale();
        }
        return resolveLocale(raw);
    }

    /** 按玩家语言读取字符串。 */
    public String text(Player player, String key) {
        return text(locale(player), key);
    }

    /** 按指定语言读取字符串，缺失时回退到默认语言。 */
    public String text(String locale, String key) {
        Map<String, String> selected = texts.getOrDefault(locale, texts.get(settings.defaultLocale()));
        String value = selected.get(key);
        if (value != null) {
            return value;
        }
        return texts.get(settings.defaultLocale())
                .getOrDefault(key, "<!i><red>Missing language key: " + key);
    }

    /** 检查玩家语言或默认语言中是否存在指定文本键。 */
    public boolean contains(Player player, String key) {
        Map<String, String> selected = texts.getOrDefault(locale(player), texts.get(settings.defaultLocale()));
        return selected.containsKey(key) || texts.get(settings.defaultLocale()).containsKey(key);
    }

    /** 检查语言文件是否存在。 */
    public boolean hasLocale(String locale) {
        return texts.containsKey(locale);
    }

    private String resolveLocale(String raw) {
        String cached = resolvedCache.get(raw);
        if (cached != null) {
            return cached;
        }
        String resolved = computeResolvedLocale(raw);
        if (resolvedCache.size() < RESOLVED_CACHE_LIMIT) {
            resolvedCache.put(raw, resolved);
        }
        return resolved;
    }

    private String computeResolvedLocale(String raw) {
        String normalized = PluginSettings.normalizeLocale(raw);
        String direct = normalizedIds.get(normalized);
        if (direct != null) {
            return direct;
        }
        String alias = settings.localeAliases().get(normalized);
        if (alias != null && texts.containsKey(alias)) {
            return alias;
        }
        if (normalized.startsWith("zh_") && texts.containsKey("zh_CN")) {
            return "zh_CN";
        }
        if (normalized.startsWith("en_") && texts.containsKey("en_US")) {
            return "en_US";
        }
        return settings.defaultLocale();
    }
}
