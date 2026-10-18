package gg.fotia.mythictools.summoning.effect;

import gg.fotia.mythictools.summoning.config.SummonNumbers;
import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

/** 已校验的粒子层快照；编辑预设不会改变进行中的仪式。 */
public record EffectLayer(String preset, EffectShape shape, boolean enabled, double start, double end,
                          double radius, double height, double speed, int density,
                          Color primary, Color secondary, double x, double y, double z,
                          boolean release, int releaseTicks, String accentParticle) {
    public static final java.util.List<String> ACCENTS = java.util.List.of("none", "FLAME", "SMOKE", "SNOWFLAKE",
            "ELECTRIC_SPARK", "PORTAL", "SPORE_BLOSSOM_AIR", "SOUL_FIRE_FLAME", "CLOUD");
    public static EffectLayer read(String preset, ConfigurationSection yaml) {
        double start = SummonNumbers.number(yaml, "start", 0, 0, 1);
        double end = SummonNumbers.number(yaml, "end", 1, 0, 1);
        if (end <= start) throw new IllegalArgumentException("end > start");
        String accent = yaml.getString("accent-particle", "none");
        if (!ACCENTS.contains(accent)) throw new IllegalArgumentException("accent-particle: " + accent);
        return new EffectLayer(preset,
                EffectShape.valueOf(yaml.getString("shape", preset).toUpperCase(Locale.ROOT)),
                yaml.getBoolean("enabled", true), start, end,
                SummonNumbers.number(yaml, "radius", 2.5, 0.1, 16),
                SummonNumbers.number(yaml, "height", 3, 0.1, 16),
                SummonNumbers.number(yaml, "speed", 1, -10, 10),
                SummonNumbers.integer(yaml, "density", 48, 8, 256),
                color(yaml.getString("primary", "#9B59FF")),
                color(yaml.getString("secondary", "#E9D5FF")),
                SummonNumbers.number(yaml, "offset.x", 0, -16, 16),
                SummonNumbers.number(yaml, "offset.y", 0.1, -16, 16),
                SummonNumbers.number(yaml, "offset.z", 0, -16, 16),
                yaml.getBoolean("release", true),
                (int) Math.round(SummonNumbers.number(yaml, "release-seconds", 1, 0.1, 10) * 20), accent);
    }

    public static Color color(String text) {
        if (text == null || !text.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("#RRGGBB");
        return Color.fromRGB(Integer.parseInt(text.substring(1), 16));
    }
}
