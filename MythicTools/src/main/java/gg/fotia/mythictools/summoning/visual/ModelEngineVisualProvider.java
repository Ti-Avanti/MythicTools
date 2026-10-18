package gg.fotia.mythictools.summoning.visual;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class ModelEngineVisualProvider implements VisualProvider {
    public String pluginName() { return "ModelEngine"; }
    public List<String> ids() { return ModelEngineAPI.getAPI().getModelRegistry().getOrderedId().stream().sorted().toList(); }
    public List<String> animations(String id) {
        var blueprint = ModelEngineAPI.getBlueprint(id);
        return blueprint == null ? List.of() : blueprint.getAnimations().keySet().stream().sorted().toList();
    }
    public VisualHandle create(VisualConfig config, Location origin, UUID viewer, double range) {
        Dummy<Void> dummy = new Dummy<>();
        dummy.setLocation(config.at(origin));
        dummy.setYBodyRot(config.yaw());
        dummy.setYHeadRot(config.yaw());
        dummy.setRenderRadius((int) range);
        dummy.setDetectingPlayers(viewer == null);
        if (viewer != null && Bukkit.getPlayer(viewer) != null) dummy.setForceViewing(Bukkit.getPlayer(viewer), true);
        var entity = ModelEngineAPI.createModeledEntity(dummy);
        try {
            entity.setBaseEntityVisible(false);
            var model = ModelEngineAPI.createActiveModel(config.id());
            model.setScale(config.scale());
            model.setCanHurt(false);
            model.setHitboxVisible(false);
            model.setShadowVisible(false);
            entity.addModel(model, false);
            return new VisualHandle() {
                private boolean closed;
                public void update(int age, List<Player> viewers) { }
                public void animate(String animation, boolean loop) {
                    if (animation.isBlank() || closed) return;
                    model.getAnimationHandler().forceStopAllAnimations();
                    var property = model.getAnimationHandler().playAnimation(animation, 0.1, 0.1, 1, true);
                    if (property != null) property.setForceLoopMode(loop
                            ? com.ticxo.modelengine.api.animation.BlueprintAnimation.LoopMode.LOOP
                            : com.ticxo.modelengine.api.animation.BlueprintAnimation.LoopMode.ONCE);
                }
                public void close() {
                    if (closed) return;
                    closed = true;
                    dummy.setRemoved(true);
                    entity.destroy();
                    ModelEngineAPI.removeModeledEntity(dummy.getUUID());
                }
            };
        } catch (RuntimeException | LinkageError error) {
            dummy.setRemoved(true);
            entity.destroy();
            ModelEngineAPI.removeModeledEntity(dummy.getUUID());
            throw error;
        }
    }
}
