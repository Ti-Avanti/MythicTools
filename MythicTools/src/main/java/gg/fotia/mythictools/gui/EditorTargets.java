package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.YamlFiles;
import gg.fotia.mythictools.version.BiomeKeys;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** 管理 GUI 编辑目标的文件定位、枚举、创建与删除。 */
final class EditorTargets {
    static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private final JavaPlugin plugin;
    private final Supplier<List<String>> mobIds;
    private final Supplier<List<String>> loadedMobGroupIds;
    private final Supplier<List<String>> loadedDropGroupIds;

    EditorTargets(
            JavaPlugin plugin,
            Supplier<List<String>> mobIds,
            Supplier<List<String>> loadedMobGroupIds,
            Supplier<List<String>> loadedDropGroupIds) {
        this.plugin = plugin;
        this.mobIds = mobIds;
        this.loadedMobGroupIds = loadedMobGroupIds;
        this.loadedDropGroupIds = loadedDropGroupIds;
    }

    record Target(File file, String rootPath) {
    }

    Target target(AdminType type, String id) {
        return switch (type) {
            case DROP_GROUP -> new Target(new File(plugin.getDataFolder(), "drops/groups/" + id + ".yml"), "");
            case MOB_DROP -> new Target(new File(plugin.getDataFolder(), "drops/mobs/" + id + ".yml"), "");
            case BIOME_RULE -> new Target(new File(plugin.getDataFolder(), "spawning/biomes.yml"), "rules." + id);
            case SPAWN_POINT -> new Target(new File(plugin.getDataFolder(), "spawning/points/" + id + ".yml"), "");
            case MOB_GROUP -> new Target(new File(plugin.getDataFolder(), "spawning/groups/" + id + ".yml"), "");
            case BOSS -> new Target(new File(plugin.getDataFolder(), "bosses/" + id + ".yml"), "");
        };
    }

    EditorSession loadSession(AdminType type, String id)
            throws IOException, InvalidConfigurationException {
        Target target = target(type, id);
        if (!target.file().isFile()) {
            throw new IllegalArgumentException("配置文件不存在: " + target.file().getName());
        }
        YamlConfiguration yaml = YamlFiles.load(target.file());
        if (!target.rootPath().isEmpty() && yaml.getConfigurationSection(target.rootPath()) == null) {
            throw new IllegalArgumentException("配置节点不存在: " + target.rootPath());
        }
        return new EditorSession(type, id, target.file(), target.rootPath(), yaml);
    }

    List<String> ids(AdminType type) {
        if (type == AdminType.BIOME_RULE) {
            try {
                YamlConfiguration yaml = YamlFiles.load(target(type, "unused").file());
                ConfigurationSection rules = yaml.getConfigurationSection("rules");
                return rules == null ? List.of() : rules.getKeys(false).stream().sorted().toList();
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().warning("无法读取群系刷怪列表: " + exception.getMessage());
                return List.of();
            }
        }
        File directory = switch (type) {
            case DROP_GROUP -> new File(plugin.getDataFolder(), "drops/groups");
            case MOB_DROP -> new File(plugin.getDataFolder(), "drops/mobs");
            case SPAWN_POINT -> new File(plugin.getDataFolder(), "spawning/points");
            case MOB_GROUP -> new File(plugin.getDataFolder(), "spawning/groups");
            case BOSS -> new File(plugin.getDataFolder(), "bosses");
            case BIOME_RULE -> throw new IllegalStateException();
        };
        File[] files = YamlFiles.list(directory);
        if (files == null) {
            return List.of();
        }
        return java.util.Arrays.stream(files)
                .map(File::getName)
                .map(name -> name.substring(0, name.length() - 4))
                .sorted()
                .toList();
    }

    void createTarget(AdminType type, String id, Location location)
            throws IOException, InvalidConfigurationException {
        Target target = target(type, id);
        YamlConfiguration yaml;
        if (type == AdminType.BIOME_RULE) {
            yaml = target.file().isFile() ? YamlFiles.load(target.file()) : new YamlConfiguration();
            if (yaml.isConfigurationSection(target.rootPath())) {
                throw new IllegalArgumentException("ID 已存在: " + id);
            }
            String root = target.rootPath() + ".";
            yaml.set(root + "enabled", true);
            applySpawnSource(yaml, root);
            yaml.set(root + "worlds", List.of(location.getWorld().getName()));
            yaml.set(root + "biomes", List.of(BiomeKeys.name(location.getBlock().getBiome())));
            yaml.set(root + "chance", 0.1);
            yaml.set(root + "interval-seconds", 30);
            yaml.set(root + "min-distance", 12);
            yaml.set(root + "max-distance", 32);
            yaml.set(root + "height.min", location.getWorld().getMinHeight());
            yaml.set(root + "height.max", location.getWorld().getMaxHeight());
            yaml.set(root + "light.min", 0);
            yaml.set(root + "light.max", 15);
            yaml.set(root + "amount.min", 1);
            yaml.set(root + "amount.max", 1);
            yaml.set(root + "limits.nearby", 3);
            yaml.set(root + "limits.global", 20);
            yaml.set(root + "limits.radius", 48);
            yaml.set(root + "level", 1.0);
            yaml.set(root + "despawn-seconds", 0);
        } else {
            if (target.file().exists()) {
                throw new IllegalArgumentException("ID 已存在: " + id);
            }
            yaml = new YamlConfiguration();
            switch (type) {
                case DROP_GROUP -> yaml.createSection("entries");
                case MOB_DROP -> {
                    String defaultGroup = CreationDraftDefaults.firstId(loadedDropGroupIds.get());
                    yaml.set("mob-id", id);
                    yaml.set("max-drops", defaultGroup == null ? 0 : 1);
                    yaml.set("experience.min", 0);
                    yaml.set("experience.max", 0);
                    yaml.set("groups", defaultGroup == null ? List.of() : List.of(Map.of(
                            "id", defaultGroup, "weight", 100, "min-amount", 1, "max-amount", 1)));
                }
                case SPAWN_POINT -> createSpawnPoint(yaml, location);
                case MOB_GROUP -> {
                    String defaultMob = CreationDraftDefaults.requireMob(mobIds.get(), "怪物组");
                    yaml.set("amount.min", 1);
                    yaml.set("amount.max", 1);
                    yaml.set("members.default.mob", defaultMob);
                    yaml.set("members.default.weight", 100);
                }
                case BOSS -> createBoss(yaml, location);
                case BIOME_RULE -> throw new IllegalStateException();
            }
        }
        YamlFiles.saveAtomically(yaml, target.file());
    }

    private void createSpawnPoint(YamlConfiguration yaml, Location location) {
        yaml.set("enabled", true);
        applySpawnSource(yaml, "");
        yaml.set("location.world", location.getWorld().getName());
        yaml.set("location.x", location.getX());
        yaml.set("location.y", location.getY());
        yaml.set("location.z", location.getZ());
        yaml.set("location.yaw", location.getYaw());
        yaml.set("location.pitch", location.getPitch());
        yaml.set("interval-seconds", 60);
        yaml.set("amount.min", 1);
        yaml.set("amount.max", 1);
        yaml.set("max-alive", 1);
        yaml.set("level", 1.0);
        yaml.set("spawn-on-death", false);
        yaml.set("despawn-seconds", 0);
    }

    private void createBoss(YamlConfiguration yaml, Location location) {
        String defaultMob = CreationDraftDefaults.requireMob(mobIds.get(), " Boss");
        String defaultGroup = CreationDraftDefaults.firstId(loadedDropGroupIds.get());
        boolean rewardsEnabled = defaultGroup != null;
        yaml.set("display.zh_CN", "<!i><red>新 Boss");
        yaml.set("display.en_US", "<!i><red>New Boss");
        yaml.set("phase-mode", "death-respawn");
        yaml.set("phases", List.of(Map.of("mob", defaultMob, "level", 1.0)));
        yaml.set("loot.intermediate-stage", "none");
        yaml.set("loot.final-stage", "mythictools-only");
        yaml.set("spawning.points.default.enabled", false);
        yaml.set("spawning.points.default.interval-seconds", 1800);
        yaml.set("spawning.points.default.max-active", 1);
        yaml.set("spawning.points.default.minimum-online-players", 0);
        yaml.set("spawning.points.default.location.world", location.getWorld().getName());
        yaml.set("spawning.points.default.location.x", location.getX());
        yaml.set("spawning.points.default.location.y", location.getY());
        yaml.set("spawning.points.default.location.z", location.getZ());
        yaml.set("broadcast.spawn.message.zh_CN", "<!i><red>Boss {boss} 已生成");
        yaml.set("broadcast.spawn.message.en_US", "<!i><red>Boss {boss} spawned");
        yaml.set("broadcast.spawn.commands", List.of());
        yaml.set("broadcast.death.message.zh_CN", "<!i><green>Boss {boss} 已被击败");
        yaml.set("broadcast.death.message.en_US", "<!i><green>Boss {boss} was defeated");
        yaml.set("broadcast.death.commands", List.of());
        yaml.set("rewards.damage-ranking.enabled", rewardsEnabled);
        yaml.set("rewards.damage-ranking.max-recipients", rewardsEnabled ? 1 : 0);
        yaml.set("rewards.damage-ranking.chat-display", true);
        if (rewardsEnabled) {
            yaml.set("rewards.damage-ranking.ranks.1", List.of(Map.of("group", defaultGroup, "copies", 1)));
        } else {
            yaml.createSection("rewards.damage-ranking.ranks");
        }
        yaml.set("rewards.killer.enabled", rewardsEnabled);
        yaml.set("rewards.killer.groups", rewardsEnabled
                ? List.of(Map.of("group", defaultGroup, "copies", 1)) : List.of());
    }

    private void applySpawnSource(YamlConfiguration yaml, String prefix) {
        CreationDraftDefaults.SpawnSource source = CreationDraftDefaults.spawnSource(
                mobIds.get(), loadedMobGroupIds.get());
        if (source.mobId() != null) {
            yaml.set(prefix + "mob", source.mobId());
            yaml.set(prefix + "mob-group", null);
        } else {
            yaml.set(prefix + "mob", null);
            yaml.set(prefix + "mob-group", source.mobGroupId());
        }
    }

    void deleteTarget(AdminType type, String id) throws IOException {
        if (!SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("无效 ID: " + id);
        }
        Target target = target(type, id);
        if (type == AdminType.BIOME_RULE) {
            try {
                YamlConfiguration yaml = YamlFiles.load(target.file());
                yaml.set(target.rootPath(), null);
                YamlFiles.saveAtomically(yaml, target.file());
            } catch (InvalidConfigurationException exception) {
                throw new IOException("群系配置格式错误", exception);
            }
        } else {
            File root = plugin.getDataFolder().getCanonicalFile();
            File candidate = target.file().getCanonicalFile();
            if (!candidate.toPath().startsWith(root.toPath())) {
                throw new IOException("拒绝删除插件目录外的文件");
            }
            Files.deleteIfExists(candidate.toPath());
        }
    }

    List<String> mobGroupReferences(String groupId) throws IOException {
        List<String> references = new ArrayList<>();
        try {
            File biomeFile = target(AdminType.BIOME_RULE, "unused").file();
            if (biomeFile.isFile()) {
                YamlConfiguration yaml = YamlFiles.load(biomeFile);
                ConfigurationSection rules = yaml.getConfigurationSection("rules");
                if (rules != null) {
                    rules.getKeys(false).stream()
                            .filter(id -> groupId.equals(rules.getString(id + ".mob-group")))
                            .forEach(id -> references.add("biome:" + id));
                }
            }
            File points = new File(plugin.getDataFolder(), "spawning/points");
            File[] files = YamlFiles.list(points);
            if (files != null) {
                for (File file : files) {
                    YamlConfiguration yaml = YamlFiles.load(file);
                    if (groupId.equals(yaml.getString("mob-group"))) {
                        references.add("point:" + file.getName().substring(0, file.getName().length() - 4));
                    }
                }
            }
            return List.copyOf(references);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("检查怪物组引用时发现配置格式错误", exception);
        }
    }
}
