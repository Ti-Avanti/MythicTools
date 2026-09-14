package gg.fotia.mythictools.config;

import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;

/** 仅迁移已知的旧版默认 GUI 布局，不覆盖用户自定义布局。 */
final class GuiLayoutMigration {
    private static final List<String> PREVIOUS_CATEGORY_LAYOUT = List.of(
            "#########",
            "##g#g#g##",
            "##g#g#g##",
            "####b####");
    private static final List<String> CURRENT_CATEGORY_LAYOUT = List.of(
            "#########",
            "##g#g#g##",
            "##g#g#g##",
            "##g#g#g##",
            "####b####");
    private static final List<String> ITEM_REWARD_EDITOR_LAYOUT = List.of(
            "#fffffff#",
            "#fffffff#",
            "#fffffff#",
            "####i####",
            "#########",
            "#p#b#s#n#");
    private static final List<String> PREVIOUS_BOSS_TIME_WINDOW_LIST_LAYOUT = List.of(
            "#eeeeeee#",
            "#eeeeeee#",
            "#eeeeeee#",
            "#eeeeeee#",
            "#########",
            "#p#b#c#n#");
    private static final List<String> CURRENT_BOSS_TIME_WINDOW_LIST_LAYOUT = List.of(
            "#eeeeeee#",
            "#eeeeeee#",
            "#eeeeeee#",
            "#eeeeeee#",
            "##z###f##",
            "#p#b#c#n#");

    private GuiLayoutMigration() {
    }

    /** 仅为仍保持内置默认布局与物品定义的旧模板启用语义材质。 */
    static boolean migrateSemanticMaterial(
            YamlConfiguration current, YamlConfiguration defaults, char symbol) {
        String path = "items." + symbol;
        if (!defaults.getBoolean(path + ".semantic-material", false)
                || current.contains(path + ".semantic-material")
                || !current.getStringList("Layout").equals(defaults.getStringList("Layout"))
                || !Objects.equals(current.getString(path + ".material"), defaults.getString(path + ".material"))
                || !Objects.equals(current.getString(path + ".display-name-key"),
                        defaults.getString(path + ".display-name-key"))
                || !current.getStringList(path + ".lore-keys")
                        .equals(defaults.getStringList(path + ".lore-keys"))) {
            return false;
        }
        current.set(path + ".semantic-material", true);
        return true;
    }

    static boolean migrateCategoryLayout(YamlConfiguration yaml) {
        if (!PREVIOUS_CATEGORY_LAYOUT.equals(yaml.getStringList("Layout"))) {
            return false;
        }
        yaml.set("Layout", CURRENT_CATEGORY_LAYOUT);
        return true;
    }

    static boolean migrateLevelingMenu(YamlConfiguration yaml) {
        if (!List.of("#########", "#d##s##b#", "#########", "#r#####c#")
                .equals(yaml.getStringList("Layout")) || yaml.contains("items.l")) {
            return false;
        }
        yaml.set("Layout", List.of("#########", "#d##s##b#", "#########", "#r##l##c#"));
        yaml.set("items.l.material", "EXPERIENCE_BOTTLE");
        yaml.set("items.l.display-name-key", "gui.main.leveling-name");
        yaml.set("items.l.lore-keys", List.of("gui.main.leveling-lore"));
        return true;
    }

    static boolean migrateItemRewardEditorTemplate(YamlConfiguration yaml) {
        if (!ITEM_REWARD_EDITOR_LAYOUT.equals(yaml.getStringList("Layout"))
                || !"NAME_TAG".equalsIgnoreCase(yaml.getString("items.f.material"))
                || !"HOPPER".equalsIgnoreCase(yaml.getString("items.i.material"))) {
            return false;
        }
        yaml.set("items.i.material", "CHEST");
        return true;
    }

    static boolean migrateBossTimeWindowListTemplate(YamlConfiguration yaml) {
        if (!PREVIOUS_BOSS_TIME_WINDOW_LIST_LAYOUT.equals(yaml.getStringList("Layout"))
                || !"CLOCK".equalsIgnoreCase(yaml.getString("items.e.material"))
                || yaml.contains("items.z")
                || yaml.contains("items.f")) {
            return false;
        }
        yaml.set("Layout", CURRENT_BOSS_TIME_WINDOW_LIST_LAYOUT);
        yaml.set("items.z.material", "COMPASS");
        yaml.set("items.z.display-name-key", "gui.boss-time-window-list.timezone-name");
        yaml.set("items.z.lore-keys", List.of("gui.boss-time-window-list.timezone-lore"));
        yaml.set("items.f.material", "REPEATER");
        yaml.set("items.f.display-name-key", "gui.boss-time-window-list.fallback-name");
        yaml.set("items.f.lore-keys", List.of("gui.boss-time-window-list.fallback-lore"));
        return true;
    }
}
