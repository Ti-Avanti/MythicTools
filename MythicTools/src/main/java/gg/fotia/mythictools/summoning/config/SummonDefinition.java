package gg.fotia.mythictools.summoning.config;

import gg.fotia.mythictools.summoning.effect.EffectLayer;
import gg.fotia.mythictools.summoning.visual.VisualConfig;
import java.util.List;
import java.util.Set;
import org.bukkit.inventory.ItemStack;

public record SummonDefinition(String id, boolean enabled, String bossId, ItemStack item,
                               int consume, long cooldownMillis, String permission, Set<String> worlds,
                               String locationMode, double targetDistance, int maxActive,
                               int windupTicks, double chargeStart, double moveRadius, boolean damageInterrupt,
                               String countdown, List<EffectLayer> layers, VisualConfig visual,
                               List<SoundCue> sounds, java.util.Map<String, String> messages) {
    public SummonDefinition {
        item = item.clone();
        worlds = Set.copyOf(worlds);
        layers = List.copyOf(layers);
        sounds = List.copyOf(sounds);
        messages = java.util.Map.copyOf(messages);
    }

    @Override public ItemStack item() { return item.clone(); }
}
