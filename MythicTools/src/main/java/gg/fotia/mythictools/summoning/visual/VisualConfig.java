package gg.fotia.mythictools.summoning.visual;

import gg.fotia.mythictools.summoning.config.SummonNumbers;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public record VisualConfig(String type, String id, boolean duringWindup, int durationTicks,
                           double scale, double x, double y, double z, float yaw,
                           boolean removeOnBossEnd, double rotation, double floatHeight,
                           String startAnimation, String chargeAnimation, String releaseAnimation,
                           String idleAnimation, String endAnimation, int endTicks, int releaseTicks) {
    public static VisualConfig read(ConfigurationSection yaml) {
        String type = yaml.getString("visual.type", "none").toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("none", "modelengine", "bettermodel", "craftengine").contains(type)) {
            throw new IllegalArgumentException("visual.type: " + type);
        }
        return new VisualConfig(type, yaml.getString("visual.id", ""),
                yaml.getBoolean("visual.during-windup", true),
                (int) Math.round(SummonNumbers.number(yaml, "visual.duration-seconds", 30, 0, 3600) * 20),
                SummonNumbers.number(yaml, "visual.scale", 1, 0.05, 20),
                SummonNumbers.number(yaml, "visual.offset.x", 0, -16, 16),
                SummonNumbers.number(yaml, "visual.offset.y", 0.1, -16, 16),
                SummonNumbers.number(yaml, "visual.offset.z", 0, -16, 16),
                (float) SummonNumbers.number(yaml, "visual.yaw", 0, -360, 360),
                yaml.getBoolean("visual.remove-on-boss-end", false),
                SummonNumbers.number(yaml, "visual.rotation", 0, -360, 360),
                SummonNumbers.number(yaml, "visual.float-height", 0, 0, 3),
                yaml.getString("visual.animations.start", ""), yaml.getString("visual.animations.charge", ""),
                yaml.getString("visual.animations.release", ""), yaml.getString("visual.animations.idle", ""),
                yaml.getString("visual.animations.end", ""),
                (int) Math.round(SummonNumbers.number(yaml, "visual.end-seconds", 0.5, 0, 10) * 20),
                (int) Math.round(SummonNumbers.number(yaml, "visual.release-seconds", 1, 0, 30) * 20));
    }

    public Location at(Location origin) {
        Location location = origin.clone().add(x, y, z);
        location.setYaw(yaw);
        location.setPitch(0);
        return location;
    }
}
