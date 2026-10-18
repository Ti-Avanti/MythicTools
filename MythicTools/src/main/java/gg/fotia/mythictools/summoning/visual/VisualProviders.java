package gg.fotia.mythictools.summoning.visual;

import java.util.*;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/** 可选类只在对应插件已启用时加载；不使用反射，不将外部实现打进 JAR。 */
public final class VisualProviders {
    private final Map<String, VisualProvider> providers = new HashMap<>();
    public VisualProviders(Plugin plugin) {
        add(plugin, "modelengine", "ModelEngine", () -> new ModelEngineVisualProvider());
        if (Runtime.version().feature() >= 25) {
            add(plugin, "bettermodel", "BetterModel", () -> new BetterModelVisualProvider());
        }
        add(plugin, "craftengine", "CraftEngine", () -> new CraftEngineVisualProvider(plugin));
    }
    private void add(Plugin plugin, String id, String dependency, Supplier<VisualProvider> factory) {
        if (!Bukkit.getPluginManager().isPluginEnabled(dependency)) return;
        try { providers.put(id, factory.get()); }
        catch (LinkageError | RuntimeException error) { plugin.getLogger().warning(dependency + ": " + error.getMessage()); }
    }
    public VisualProvider get(String type) {
        VisualProvider provider = providers.get(type);
        return provider != null && Bukkit.getPluginManager().isPluginEnabled(provider.pluginName()) ? provider : null;
    }
    public List<String> ids(String type) {
        VisualProvider provider = get(type);
        return provider == null ? List.of() : provider.ids();
    }
    public List<String> animations(String type, String id) {
        VisualProvider provider = get(type);
        return provider == null ? List.of() : provider.animations(id);
    }
    public void validate(VisualConfig config) {
        if (config.type().equals("none")) return;
        VisualProvider provider = get(config.type());
        if (provider == null) throw new IllegalArgumentException("summon.error.dependency");
        if (!provider.ids().contains(config.id())) throw new IllegalArgumentException("summon.error.visual-id");
        var animations = provider.animations(config.id());
        for (String animation : List.of(config.startAnimation(), config.chargeAnimation(), config.releaseAnimation(),
                config.idleAnimation(), config.endAnimation())) {
            if (!animation.isBlank() && !animations.contains(animation)) throw new IllegalArgumentException("summon.error.animation");
        }
    }
    public VisualHandle create(VisualConfig config, Location origin, UUID viewer, double range) {
        validate(config);
        return config.type().equals("none") ? VisualHandle.NONE : get(config.type()).create(config, origin, viewer, range);
    }
}
