package gg.fotia.mythictools.gui;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

/** 距离等级配置的可编辑初始值；规则组配置完整后再启用。 */
final class LevelEditorDefaults {
    private LevelEditorDefaults() {
    }

    static void fillMissing(YamlConfiguration yaml, AdminType type) {
        if (type.domain() != gg.fotia.mythictools.config.ConfigDomain.LEVELING) {
            return;
        }
        YamlConfiguration defaults = new YamlConfiguration();
        if (type == AdminType.LEVEL_GROUP) {
            apply(defaults, type, null);
        } else {
            defaults.set("enabled", true);
            defaults.set("location.world", "");
            if (type == AdminType.LEVEL_POINT) {
                defaults.set("location.x", 0.0);
                defaults.set("location.z", 0.0);
            } else {
                defaults.set("region-id", "");
            }
        }
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !yaml.contains(key)) {
                yaml.set(key, defaults.get(key));
            }
        }
    }

    static void apply(YamlConfiguration yaml, AdminType type, Location location) {
        yaml.set("enabled", type == AdminType.LEVEL_POINT);
        if (type == AdminType.LEVEL_GROUP) {
            yaml.set("priority", 0);
            yaml.set("mob-ids", List.of());
            yaml.set("mob-groups", List.of());
            yaml.set("point-ids", List.of());
            yaml.set("region-ids", List.of());
            yaml.set("level-mode", "replace");
            yaml.set("base-level", 1.0);
            yaml.set("safe-distance", 0.0);
            yaml.set("distance-per-level", 100.0);
            yaml.set("level-increase", 1.0);
            yaml.set("max-level", 100.0);
            return;
        }
        yaml.set("location.world", location.getWorld().getName());
        if (type == AdminType.LEVEL_POINT) {
            yaml.set("location.x", location.getX());
            yaml.set("location.z", location.getZ());
        } else {
            yaml.set("region-id", "");
        }
    }
}
