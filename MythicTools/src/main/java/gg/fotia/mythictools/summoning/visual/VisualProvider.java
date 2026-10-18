package gg.fotia.mythictools.summoning.visual;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;

public interface VisualProvider {
    String pluginName();
    List<String> ids();
    List<String> animations(String id);
    VisualHandle create(VisualConfig config, Location origin, UUID viewer, double range);
}
