package gg.fotia.mythictools.summoning.effect;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** 所有形状共用采样、颜色与预算控制，不创建粒子承载实体。 */
public final class EffectCanvas {
    private static final Particle DUST = dust();
    public final EffectLayer layer;
    public final double progress;
    public final double angle;
    public final int samples;
    private final Location origin;
    private final List<Player> viewers;
    private final ParticleBudget budget;
    private final Particle.DustOptions primary;
    private final Particle.DustOptions secondary;
    private final Particle accentParticle;
    private int emitted;

    public EffectCanvas(EffectLayer layer, Location origin, List<Player> viewers, ParticleBudget budget,
                        double progress, double seconds, double quality) {
        this.layer = layer;
        this.origin = origin.clone().add(layer.x(), layer.y(), layer.z());
        this.viewers = viewers;
        this.budget = budget;
        this.progress = progress;
        this.angle = seconds * layer.speed() * 2;
        this.samples = Math.max(8, (int) (layer.density() * quality));
        this.primary = new Particle.DustOptions(layer.primary(), 1.1f);
        this.secondary = new Particle.DustOptions(layer.secondary(), 0.85f);
        this.accentParticle = accent(layer.accentParticle());
    }

    public void dot(double x, double y, double z, boolean accent) {
        boolean extra = accentParticle != null && emitted++ % 12 == 0;
        for (Player viewer : viewers) {
            if (!budget.take()) return;
            viewer.spawnParticle(DUST, origin.getX() + x, origin.getY() + y, origin.getZ() + z,
                    1, 0, 0, 0, 0, accent ? secondary : primary);
            if (extra && budget.take()) viewer.spawnParticle(accentParticle, origin.getX() + x, origin.getY() + y,
                    origin.getZ() + z, 1, 0.03, 0.03, 0.03, 0.01);
        }
    }

    public void ring(double radius, double height, double rotation, boolean vertical, boolean accent) {
        for (int i = 0; i < samples; i++) {
            double a = Math.PI * 2 * i / samples + rotation;
            if (vertical) dot(Math.cos(a) * radius, height + Math.sin(a) * radius, 0, accent);
            else dot(Math.cos(a) * radius, height, Math.sin(a) * radius, accent);
        }
    }

    public void ray(double x1, double y1, double z1, double x2, double y2, double z2, int count, boolean accent) {
        for (int i = 0; i <= count; i++) {
            double t = (double) i / count;
            dot(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, accent);
        }
    }

    private static Particle dust() {
        try { return Particle.valueOf("DUST"); }
        catch (IllegalArgumentException ignored) { return Particle.valueOf("REDSTONE"); }
    }

    public static double fraction(double value) { return value - Math.floor(value); }

    private static Particle accent(String name) {
        if (name.equals("none")) return null;
        try { return Particle.valueOf(name); }
        catch (IllegalArgumentException error) {
            if (name.equals("SMOKE")) return Particle.valueOf("SMOKE_NORMAL");
            throw error;
        }
    }
}
