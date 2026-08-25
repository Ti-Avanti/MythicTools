package gg.fotia.mythictools.blackboxtest;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** 测试辅助插件的双语消息读取器。 */
final class TestMessages {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();

    TestMessages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        for (String locale : new String[]{"zh_CN", "en_US"}) {
            String path = "lang/" + locale + ".yml";
            File file = new File(plugin.getDataFolder(), path);
            if (!file.isFile()) {
                plugin.saveResource(path, false);
            }
            languages.put(locale, YamlConfiguration.loadConfiguration(file));
        }
    }

    void send(CommandSender sender, String key, Map<String, ?> variables) {
        String locale = sender instanceof Player player
                && !player.getLocale().toLowerCase(Locale.ROOT).startsWith("zh") ? "en_US" : "zh_CN";
        String text = languages.get(locale).getString(key,
                languages.get("zh_CN").getString(key, "MTTEST_ERROR reason=missing-language-key"));
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        sender.sendMessage(text);
    }
}
