package gg.fotia.mythictools.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** 提供 Boss 生成器列表、字段隔离与安全默认配置。 */
final class BossSpawnerEditorFields {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private BossSpawnerEditorFields() {
    }

    static List<String> withMinimumOnlinePlayers(
            YamlConfiguration yaml, EditorCategory category, List<String> currentFields) {
        String rootPath = rootPathOrNull(category);
        if (rootPath == null) {
            return List.copyOf(currentFields);
        }
        List<String> fields = new ArrayList<>(currentFields);
        ConfigurationSection root = yaml.getConfigurationSection(rootPath);
        if (root != null) {
            for (String id : root.getKeys(false)) {
                String field = rootPath + "." + id + ".minimum-online-players";
                if (!fields.contains(field)) {
                    fields.add(field);
                }
            }
        }
        fields.sort(String::compareTo);
        return List.copyOf(fields);
    }

    static Object defaultValue(String field) {
        return field.endsWith(".minimum-online-players") ? 0 : null;
    }

    static List<String> ids(YamlConfiguration yaml, EditorCategory category) {
        ConfigurationSection root = yaml.getConfigurationSection(rootPath(category));
        return root == null ? List.of() : root.getKeys(false).stream().sorted().toList();
    }

    static List<String> fields(YamlConfiguration yaml, EditorCategory category, String spawnerId) {
        String root = spawnerPath(category, spawnerId);
        ConfigurationSection section = yaml.getConfigurationSection(root);
        if (section == null) {
            throw new IllegalArgumentException("Boss 生成器不存在: " + spawnerId);
        }
        List<String> fields = new ArrayList<>(section.getKeys(true).stream()
                .filter(path -> !section.isConfigurationSection(path))
                .map(path -> root + "." + path)
                .filter(path -> category != EditorCategory.BOSS_POINT_SPAWNING
                        || !path.contains(".schedule.time-windows."))
                .sorted()
                .toList());
        String minimumOnlinePlayers = root + ".minimum-online-players";
        if (!fields.contains(minimumOnlinePlayers)) {
            fields.add(minimumOnlinePlayers);
            fields.sort(String::compareTo);
        }
        return List.copyOf(fields);
    }

    static String requireCreatableId(
            YamlConfiguration yaml, EditorCategory category, String input) {
        String id = input == null ? "" : input.trim();
        if (!SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("无效的 Boss 生成器 ID");
        }
        if (yaml.contains(spawnerPath(category, id))) {
            throw new IllegalArgumentException("Boss 生成器 ID 已存在: " + id);
        }
        return id;
    }

    static void createBiome(
            YamlConfiguration yaml, String inputId, String world, String biome) {
        String id = requireCreatableId(yaml, EditorCategory.BOSS_BIOME_SPAWNING, inputId);
        String root = spawnerPath(EditorCategory.BOSS_BIOME_SPAWNING, id) + ".";
        yaml.set(root + "enabled", false);
        yaml.set(root + "worlds", List.of(world));
        yaml.set(root + "biomes", List.of(biome));
        yaml.set(root + "min-distance", 12);
        yaml.set(root + "max-distance", 32);
        yaml.set(root + "interval-seconds", 300L);
        yaml.set(root + "chance", 0.01D);
        yaml.set(root + "max-active", 1);
        yaml.set(root + "minimum-online-players", 0);
    }

    static void createPoint(
            YamlConfiguration yaml,
            String inputId,
            String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String timezone) {
        String id = requireCreatableId(yaml, EditorCategory.BOSS_POINT_SPAWNING, inputId);
        String root = spawnerPath(EditorCategory.BOSS_POINT_SPAWNING, id) + ".";
        yaml.set(root + "enabled", false);
        yaml.set(root + "location.world", world);
        yaml.set(root + "location.x", x);
        yaml.set(root + "location.y", y);
        yaml.set(root + "location.z", z);
        yaml.set(root + "location.yaw", yaw);
        yaml.set(root + "location.pitch", pitch);
        yaml.set(root + "max-active", 1);
        yaml.set(root + "minimum-online-players", 0);
        yaml.set(root + "schedule.timezone", timezone);
        yaml.set(root + "schedule.fallback-interval-seconds", 7200L);
    }

    static void remove(YamlConfiguration yaml, EditorCategory category, String spawnerId) {
        String path = spawnerPath(category, spawnerId);
        if (!yaml.contains(path)) {
            throw new IllegalArgumentException("Boss 生成器不存在: " + spawnerId);
        }
        yaml.set(path, null);
    }

    static String relativeField(EditorCategory category, String spawnerId, String field) {
        String prefix = spawnerPath(category, spawnerId) + ".";
        if (!field.startsWith(prefix)) {
            throw new IllegalArgumentException("字段不属于所选 Boss 生成器: " + field);
        }
        return field.substring(prefix.length());
    }

    static String spawnerIdFromField(EditorCategory category, String field) {
        String prefix = rootPath(category) + ".";
        if (!field.startsWith(prefix)) {
            return null;
        }
        int end = field.indexOf('.', prefix.length());
        return end < 0 ? null : field.substring(prefix.length(), end);
    }

    static String spawnerPath(EditorCategory category, String spawnerId) {
        if (spawnerId == null || !SAFE_ID.matcher(spawnerId).matches()) {
            throw new IllegalArgumentException("无效的 Boss 生成器 ID");
        }
        return rootPath(category) + "." + spawnerId;
    }

    private static String rootPath(EditorCategory category) {
        String root = rootPathOrNull(category);
        if (root == null) {
            throw new IllegalArgumentException("该分类不是 Boss 生成器分类: " + category);
        }
        return root;
    }

    private static String rootPathOrNull(EditorCategory category) {
        return switch (category) {
            case BOSS_POINT_SPAWNING -> "spawning.points";
            case BOSS_BIOME_SPAWNING -> "spawning.biome";
            default -> null;
        };
    }
}
