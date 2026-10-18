package gg.fotia.mythictools.summoning.visual;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

final class CraftEngineVisualProvider implements VisualProvider {
    private final Plugin plugin;
    CraftEngineVisualProvider(Plugin plugin) { this.plugin = plugin; }
    public String pluginName() { return "CraftEngine"; }
    public List<String> ids() { return CraftEngineItems.loadedItems().keySet().stream().map(Object::toString).sorted().toList(); }
    public List<String> animations(String id) { return List.of(); }
    public VisualHandle create(VisualConfig config, Location origin, UUID viewer, double range) {
        var item = CraftEngineItems.byId(config.id()).buildBukkitItem();
        Location fixed = config.at(origin);
        ItemDisplay display = origin.getWorld().spawn(fixed, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setItemStack(item);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setViewRange((float) (range / 64));
            entity.setInterpolationDuration(2);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f((float) config.scale()), new AxisAngle4f()));
        });
        if (viewer != null) for (Player player : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player.getUniqueId())) player.hideEntity(plugin, display);
        }
        return new VisualHandle() {
            public void update(int age, List<Player> viewers) {
                if (!display.isValid()) return;
                for (Player player : fixed.getWorld().getPlayers()) {
                    if (viewers.contains(player)) player.showEntity(plugin, display); else player.hideEntity(plugin, display);
                }
                display.setRotation(config.yaw() + (float) (age / 20.0 * config.rotation()), 0);
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(
                        new Vector3f(0, (float) (Math.sin(age / 10.0) * config.floatHeight()), 0),
                        new AxisAngle4f(), new Vector3f((float) config.scale()), new AxisAngle4f()));
            }
            public void animate(String animation, boolean loop) { }
            public void close() { display.remove(); }
        };
    }
}
