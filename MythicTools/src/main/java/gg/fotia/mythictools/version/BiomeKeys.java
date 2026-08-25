package gg.fotia.mythictools.version;

import java.util.Locale;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;

/** 通过稳定 Registry/Keyed 接口跨越 Biome 枚举到注册表类型的变化。 */
public final class BiomeKeys {
    private BiomeKeys() {
    }

    /** 将配置中的群系名解析为 Bukkit 群系。 */
    public static Biome parse(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(normalized.contains(":")
                ? normalized : "minecraft:" + normalized);
        Biome biome = key == null ? null : Registry.BIOME.get(key);
        if (biome == null) {
            throw new IllegalArgumentException("未知生物群系: " + input);
        }
        return biome;
    }

    /** 返回不带 minecraft 命名空间的配置名称。 */
    public static String name(Biome biome) {
        return ((Keyed) biome).getKey().getKey().toUpperCase(Locale.ROOT);
    }
}
