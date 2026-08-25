package gg.fotia.mythictools.text;

import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.lang.LocaleService;
import gg.fotia.mythictools.platform.PaperPlatformBridge;
import gg.fotia.mythictools.platform.PlatformBridge;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import java.util.logging.Logger;

/** 统一处理业务变量、PAPI、旧颜色码、MiniMessage 与 CraftEngine 图片。 */
public final class MessageRenderer {
    private static final Pattern IMAGE_TAG = Pattern.compile("(?i)<image:[^>]+>");
    private static final int RENDER_CACHE_LIMIT = 512;
    private final LocaleService localeService;
    private final PluginSettings settings;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final CraftEngineTextAdapter craftEngineAdapter;
    private final PlatformBridge platform;
    private final Logger logger;
    /** 变量与 PAPI 展开后的最终文本到组件的解析缓存；组件不可变可安全共享。 */
    private final Map<String, Component> renderCache = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
                    return size() > RENDER_CACHE_LIMIT;
                }
            });
    /** 首次出现的展开文本暂不占用正式缓存，避免动态值挤出高复用组件。 */
    private final Map<String, Boolean> renderCacheCandidates = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > RENDER_CACHE_LIMIT / 4;
                }
            });

    public MessageRenderer(
            LocaleService localeService,
            PluginSettings settings,
            PluginManager pluginManager,
            Logger logger) {
        this(localeService, settings, pluginManager, logger, new PaperPlatformBridge());
    }

    public MessageRenderer(
            LocaleService localeService,
            PluginSettings settings,
            PluginManager pluginManager,
            Logger logger,
            PlatformBridge platform) {
        this.localeService = localeService;
        this.settings = settings;
        this.logger = logger;
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        CraftEngineTextAdapter adapter = null;
        if (pluginManager.isPluginEnabled("CraftEngine")) {
            try {
                adapter = new CraftEngineTextAdapter();
            } catch (LinkageError error) {
                logger.warning("CraftEngine 文本桥接不可用，将使用图片占位文本: " + error.getMessage());
            }
        }
        this.craftEngineAdapter = adapter;
    }

    /** 按玩家语言读取语言键并渲染。 */
    public Component renderKey(Player player, String key, Map<String, ?> variables) {
        return render(localeService.text(player, key), player, variables);
    }

    /** 读取玩家语言对应的原始文本，供组合语言键使用。 */
    public String text(Player player, String key) {
        return localeService.text(player, key);
    }

    /** 检查玩家语言或默认语言中是否存在指定文本键。 */
    public boolean containsText(Player player, String key) {
        return localeService.contains(player, key);
    }

    /** 按换行拆分语言键，适用于物品 Lore。 */
    public List<Component> renderKeyLines(Player player, String key, Map<String, ?> variables) {
        List<Component> lines = new ArrayList<>();
        for (String line : localeService.text(player, key).split("\\n", -1)) {
            lines.add(render("<!i>" + line, player, variables));
        }
        return List.copyOf(lines);
    }

    /** 按指定语言读取语言键并渲染。 */
    public Component renderKey(String locale, String key, Player context, Map<String, ?> variables) {
        return render(localeService.text(locale, key), context, variables);
    }

    /** 渲染任意配置文本。 */
    public Component render(String input, Player player, Map<String, ?> variables) {
        String text = input == null ? "" : input;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        if (player != null && text.indexOf('%') >= 0) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        text = LegacyColorConverter.convert(text);
        Component cached = renderCache.get(text);
        if (cached != null) {
            return cached;
        }
        try {
            Component rendered;
            if (craftEngineAdapter != null) {
                rendered = craftEngineAdapter.render(text);
            } else {
                String fallback = settings.missingImageFallback();
                rendered = miniMessage.deserialize(IMAGE_TAG.matcher(text).replaceAll(
                        java.util.regex.Matcher.quoteReplacement(fallback)));
            }
            if (renderCacheCandidates.put(text, Boolean.TRUE) != null) {
                renderCacheCandidates.remove(text);
                renderCache.put(text, rendered);
            }
            return rendered;
        } catch (Throwable exception) {
            logger.log(java.util.logging.Level.WARNING, "渲染 MiniMessage 文本失败，已降级为纯文本: " + text, exception);
            return Component.text(MiniMessage.miniMessage().stripTags(text));
        }
    }

    /** 向玩家发送语言键对应消息。 */
    public void send(Player player, String key, Map<String, ?> variables) {
        platform.send(player, renderKey(player, key, variables));
    }

    /** 通过当前服务端桥接发送已渲染组件。 */
    public void send(CommandSender sender, Component component) {
        platform.send(sender, component);
    }

    /** 返回当前运行时选定的平台桥接。 */
    public PlatformBridge platform() {
        return platform;
    }
}
