package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.version.BiomeKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;

/** 为可由服务端直接枚举的字段提供候选值和选择模式。 */
public enum FieldSelectorType {
    BIOMES(true),
    WORLDS(true),
    WORLD(false),
    MOB_GROUP(false),
    MYTHIC_MOB(false),
    BOSS_PHASE_MODE(false),
    BOSS_INTERMEDIATE_LOOT(false),
    BOSS_FINAL_LOOT(false);

    private final boolean multiple;

    FieldSelectorType(boolean multiple) {
        this.multiple = multiple;
    }

    public boolean multiple() {
        return multiple;
    }

    /** 根据相对 YAML 字段路径判断是否应打开二级选择菜单。 */
    public static FieldSelectorType fromField(String field) {
        if (field.equals("phase-mode")) {
            return BOSS_PHASE_MODE;
        }
        if (field.equals("loot.intermediate-stage")) {
            return BOSS_INTERMEDIATE_LOOT;
        }
        if (field.equals("loot.final-stage")) {
            return BOSS_FINAL_LOOT;
        }
        String[] segments = field.split("\\.");
        String leaf = segments[segments.length - 1];
        if (leaf.equals("biomes")) {
            return BIOMES;
        }
        if (leaf.equals("worlds")) {
            return WORLDS;
        }
        if (leaf.equals("world") && segments.length > 1
                && segments[segments.length - 2].equals("location")) {
            return WORLD;
        }
        if (leaf.equals("mob-group")) {
            return MOB_GROUP;
        }
        if (leaf.equals("mob") || leaf.equals("mob-id")) {
            return MYTHIC_MOB;
        }
        return null;
    }

    /** 从当前服务端注册表或已加载世界中读取候选值。 */
    public List<String> options() {
        List<String> values = new ArrayList<>();
        if (this == BIOMES) {
            for (Biome biome : Registry.BIOME) {
                values.add(BiomeKeys.name(biome));
            }
        } else if (this == WORLDS || this == WORLD) {
            values.addAll(Bukkit.getWorlds().stream().map(World::getName).toList());
        } else if (this == BOSS_PHASE_MODE) {
            values.addAll(List.of("death-respawn", "mythic-native"));
        } else if (this == BOSS_INTERMEDIATE_LOOT) {
            values.addAll(List.of("legacy", "none", "mythic"));
        } else if (this == BOSS_FINAL_LOOT) {
            values.addAll(List.of("legacy", "mythictools-only", "mythic-only", "combined"));
        }
        values.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(values);
    }

    /** 返回群系的原版翻译标签；无法解析时回退到原始值。 */
    public String translatedName(String value) {
        if (this != BIOMES) {
            return value;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(
                normalized.contains(":") ? normalized : "minecraft:" + normalized);
        if (key == null || Registry.BIOME.get(key) == null) {
            return value;
        }
        return "<lang:biome." + key.getNamespace() + "." + key.getKey() + ">";
    }
}
