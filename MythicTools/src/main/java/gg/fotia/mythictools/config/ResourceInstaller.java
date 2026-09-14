package gg.fotia.mythictools.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** 负责释放插件首次启动所需的默认资源。 */
public final class ResourceInstaller {
    private static final List<String> DEFAULT_RESOURCES = List.of(
            "config.yml",
            "placeholders.yml",
            "rarities.yml",
            "lang/zh_CN.yml",
            "lang/en_US.yml",
            "gui/main.yml",
            "gui/list.yml",
            "gui/category.yml",
            "gui/editor.yml",
            "gui/section-editor.yml",
            "gui/reward-list.yml",
            "gui/item-reward-editor.yml",
            "gui/command-reward-editor.yml",
            "gui/reward-rarity-selector.yml",
            "gui/reward-option-selector.yml",
            "gui/reward-grant-mode-selector.yml",
            "gui/first-defeat-menu.yml",
            "gui/first-defeat-reward-list.yml",
            "gui/first-defeat-reward-selector.yml",
            "gui/drops-menu.yml",
            "gui/spawning-menu.yml",
            "gui/leveling-menu.yml",
            "gui/mob-member-list.yml",
            "gui/mob-member-editor.yml",
            "gui/mob-drop-group-list.yml",
            "gui/mob-drop-group-editor.yml",
            "gui/boss-phase-list.yml",
            "gui/boss-phase-editor.yml",
            "gui/boss-point-schedule-list.yml",
            "gui/boss-time-window-list.yml",
            "gui/boss-time-window-editor.yml",
            "gui/boss-ranking-reward-menu.yml",
            "gui/boss-reward-rank-list.yml",
            "gui/boss-reward-group-list.yml",
            "gui/boss-reward-group-editor.yml",
            "gui/boss-spawner-list.yml",
            "gui/boss-spawner-editor.yml",
            "gui/selector.yml",
            "gui/confirm.yml",
            "drops/groups/example.yml",
            "drops/mobs/SkeletalMinion.yml",
            "spawning/biomes.yml",
            "spawning/groups/example.yml",
            "spawning/points/example.yml",
            "bosses/example.yml",
            "leveling/groups/example.yml",
            "leveling/points/example.yml",
            "leveling/regions/example.yml");

    private ResourceInstaller() {
    }

    /** 释放所有尚不存在的默认资源，不覆盖用户配置。 */
    public static void install(JavaPlugin plugin) {
        for (String path : DEFAULT_RESOURCES) {
            if (!new File(plugin.getDataFolder(), path).isFile()) {
                plugin.saveResource(path, false);
            }
        }
        for (String path : List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml")) {
            mergeMissingKeys(plugin, path);
        }
        migrateCategoryLayout(plugin);
        migrateLevelingMenu(plugin);
        migrateItemRewardEditorTemplate(plugin);
        migrateBossTimeWindowListTemplate(plugin);
        migrateSemanticMaterialTemplates(plugin);
    }

    private static void mergeMissingKeys(JavaPlugin plugin, String path) {
        File file = new File(plugin.getDataFolder(), path);
        try (var stream = plugin.getResource(path)) {
            if (stream == null) {
                throw new IOException("JAR 中缺少资源 " + path);
            }
            YamlConfiguration current = new YamlConfiguration();
            current.options().parseComments(true);
            current.load(file);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !current.contains(key)) {
                    current.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                current.save(file);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "无法合并默认资源 " + path, exception);
        }
    }

    private static void migrateCategoryLayout(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "gui/category.yml");
        try {
            YamlConfiguration current = YamlFiles.load(file);
            if (GuiLayoutMigration.migrateCategoryLayout(current)) {
                YamlFiles.saveAtomically(current, file);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "无法迁移默认分类菜单布局", exception);
        }
    }

    private static void migrateLevelingMenu(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "gui/main.yml");
        try {
            YamlConfiguration current = YamlFiles.load(file);
            if (GuiLayoutMigration.migrateLevelingMenu(current)) {
                YamlFiles.saveAtomically(current, file);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "无法添加距离等级菜单入口，可使用 /mt levels 打开", exception);
        }
    }

    private static void migrateItemRewardEditorTemplate(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "gui/item-reward-editor.yml");
        try {
            YamlConfiguration current = YamlFiles.load(file);
            if (GuiLayoutMigration.migrateItemRewardEditorTemplate(current)) {
                YamlFiles.saveAtomically(current, file);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "无法迁移默认物品奖励编辑菜单", exception);
        }
    }

    private static void migrateBossTimeWindowListTemplate(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "gui/boss-time-window-list.yml");
        try {
            YamlConfiguration current = YamlFiles.load(file);
            if (GuiLayoutMigration.migrateBossTimeWindowListTemplate(current)) {
                YamlFiles.saveAtomically(current, file);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "无法迁移默认 Boss 时间窗口菜单", exception);
        }
    }

    private static void migrateSemanticMaterialTemplates(JavaPlugin plugin) {
        for (String path : List.of(
                "gui/category.yml",
                "gui/editor.yml",
                "gui/section-editor.yml",
                "gui/item-reward-editor.yml",
                "gui/command-reward-editor.yml")) {
            File file = new File(plugin.getDataFolder(), path);
            try (var stream = plugin.getResource(path)) {
                if (stream == null) {
                    throw new IOException("JAR 中缺少资源 " + path);
                }
                YamlConfiguration current = YamlFiles.load(file);
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                char symbol = path.endsWith("category.yml") ? 'g' : 'f';
                if (GuiLayoutMigration.migrateSemanticMaterial(current, defaults, symbol)) {
                    YamlFiles.saveAtomically(current, file);
                }
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().log(Level.WARNING, "无法迁移语义材质配置 " + path, exception);
            }
        }
    }
}
