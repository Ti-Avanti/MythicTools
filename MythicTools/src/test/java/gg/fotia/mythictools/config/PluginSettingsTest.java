package gg.fotia.mythictools.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginSettingsTest {
    @Test
    void defaultLocaleUsesConfiguredAliasTarget() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Language.Default", "ZH-cn");
        yaml.set("Language.Locale-Aliases.zh_cn", "zh_CN");

        PluginSettings settings = PluginSettings.load(yaml);

        assertEquals("zh_CN", settings.defaultLocale());
    }

    @Test
    void customDefaultLocaleKeepsItsConfiguredIdentifier() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Language.Default", "pt_BR");

        PluginSettings settings = PluginSettings.load(yaml);

        assertEquals("pt_BR", settings.defaultLocale());
    }
}
