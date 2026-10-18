package gg.fotia.mythictools.summoning.effect;

import gg.fotia.mythictools.summoning.config.SummonSettings;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ParticleEffects {
    private final SummonSettings settings;
    private final ParticleBudget budget = new ParticleBudget();
    private final Map<EffectShape, ParticlePattern> patterns = Map.of(
            EffectShape.ARCANE, new ArcanePattern(), EffectShape.FLAME, new FlamePattern(),
            EffectShape.FROST, new FrostPattern(), EffectShape.THUNDER, new ThunderPattern(),
            EffectShape.VOID, new VoidPattern(), EffectShape.NATURE, new NaturePattern(),
            EffectShape.SOUL, new SoulPattern(), EffectShape.SAND, new SandPattern());
    public ParticleEffects(SummonSettings settings) { this.settings = settings; }
    public void beginFrame() { budget.reset(settings.particleBudget()); }
    public List<Player> viewers(Location origin, UUID only) {
        double range = settings.range() * settings.range();
        return origin.getWorld().getPlayers().stream().filter(p -> (only == null || only.equals(p.getUniqueId()))
                && p.getLocation().distanceSquared(origin) <= range).toList();
    }
    public void render(List<EffectLayer> layers, Location origin, UUID only, int ticks, int duration, boolean release) {
        List<Player> viewers = viewers(origin, only);
        if (viewers.isEmpty()) return;
        for (EffectLayer layer : layers) {
            if (!layer.enabled()) continue;
            double progress = Math.min(1, ticks / (double) (release ? layer.releaseTicks() : duration));
            if (release ? !layer.release() || ticks > layer.releaseTicks()
                    : progress < layer.start() || progress > layer.end()) continue;
            double local = release ? progress : (progress - layer.start()) / (layer.end() - layer.start());
            EffectCanvas canvas = new EffectCanvas(layer, origin, viewers, budget, local, ticks / 20.0, settings.quality());
            if (release) patterns.get(layer.shape()).release(canvas); else patterns.get(layer.shape()).charge(canvas);
        }
    }
}
