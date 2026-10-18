package gg.fotia.mythictools.summoning.visual;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.tracker.ModelScaler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** 独立编译较新 Java API，核心模块继续保持 Java 17 字节码。 */
final class BetterModelVisualProvider implements VisualProvider {
    public String pluginName() { return "BetterModel"; }
    public List<String> ids() { return BetterModel.models().stream().map(m -> m.name()).sorted().toList(); }
    public List<String> animations(String id) {
        return BetterModel.model(id).map(m -> m.animations().keySet().stream().sorted().toList()).orElse(List.of());
    }
    public VisualHandle create(VisualConfig config, Location origin, UUID viewer, double range) {
        var renderer = BetterModel.model(config.id()).orElseThrow();
        Set<UUID> audience = ConcurrentHashMap.newKeySet();
        var tracker = renderer.create(BukkitAdapter.adapt(config.at(origin)),
                kr.toxicity.model.api.tracker.TrackerModifier.DEFAULT, created -> {
                    created.scaler(ModelScaler.value((float) config.scale()));
                    created.getPipeline().viewFilter(p -> audience.contains(p.uuid()));
                });
        return new VisualHandle() {
            private String current = "";
            public void update(int age, List<Player> viewers) {
                var visible = viewers.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
                for (UUID previous : List.copyOf(audience)) if (!visible.contains(previous)) {
                    Player player = org.bukkit.Bukkit.getPlayer(previous);
                    if (player != null) tracker.remove(BukkitAdapter.adapt(player));
                }
                audience.retainAll(visible);
                for (Player player : viewers) {
                    if (audience.add(player.getUniqueId())) tracker.spawn(BukkitAdapter.adapt(player));
                }
            }
            public void animate(String animation, boolean loop) {
                if (animation.isBlank()) return;
                if (!current.isBlank()) tracker.stopAnimation(current);
                current = animation;
                tracker.animate(animation, loop ? AnimationModifier.DEFAULT : AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
            }
            public void close() { audience.clear(); tracker.close(); }
        };
    }
}
