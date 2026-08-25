package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.YamlFiles;
import gg.fotia.mythictools.item.ItemFactory;
import gg.fotia.mythictools.text.MessageRenderer;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;

/** 加载并校验全部 GUI 布局模板。 */
public final class GuiTemplateRepository {
    private final JavaPlugin plugin;
    private final MessageRenderer messages;
    private final ItemFactory items;
    private final Map<String, GuiTemplate> templates = new HashMap<>();

    public GuiTemplateRepository(JavaPlugin plugin, MessageRenderer messages, ItemFactory items) {
        this.plugin = plugin;
        this.messages = messages;
        this.items = items;
    }

    /** 完整校验后一次替换全部管理界面模板。 */
    public void reload() {
        Map<String, GuiTemplate> loaded = new HashMap<>();
        for (String id : new String[]{
                "main", "list", "category", "editor", "section-editor", "reward-list",
                "item-reward-editor", "command-reward-editor", "drops-menu", "spawning-menu",
                "reward-rarity-selector", "reward-option-selector",
                "mob-member-list", "mob-member-editor",
                "mob-drop-group-list", "mob-drop-group-editor",
                "boss-phase-list", "boss-phase-editor", "boss-point-schedule-list",
                "boss-time-window-list", "boss-time-window-editor",
                "boss-ranking-reward-menu", "boss-reward-rank-list",
                "boss-reward-group-list", "boss-reward-group-editor",
                "boss-spawner-list", "boss-spawner-editor", "selector", "confirm"}) {
            File file = new File(plugin.getDataFolder(), "gui/" + id + ".yml");
            try {
                loaded.put(id, new GuiTemplate(id, YamlFiles.load(file), messages, items));
            } catch (IOException | InvalidConfigurationException exception) {
                throw new IllegalStateException("无法加载 GUI 配置 " + file.getAbsolutePath(), exception);
            }
        }
        templates.clear();
        templates.putAll(loaded);
    }

    public GuiTemplate get(String id) {
        GuiTemplate template = templates.get(id);
        if (template == null) {
            throw new IllegalArgumentException("GUI 模板不存在: " + id);
        }
        return template;
    }
}
