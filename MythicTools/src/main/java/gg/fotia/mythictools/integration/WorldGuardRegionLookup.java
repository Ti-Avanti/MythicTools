package gg.fotia.mythictools.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import gg.fotia.mythictools.leveling.HorizontalShape;
import gg.fotia.mythictools.leveling.RegionLookup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;

/** WorldGuard 7 区域桥接，仅在依赖存在时实例化，短期缓存区域的水平轮廓。 */
public final class WorldGuardRegionLookup implements RegionLookup {
    private final long cacheNanos;
    private final Map<Key, Cached> cache = new HashMap<>();

    public WorldGuardRegionLookup(long cacheMillis) {
        cacheNanos = TimeUnit.MILLISECONDS.toNanos(cacheMillis);
    }

    @Override
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    @Override
    public Optional<HorizontalShape> shape(String world, String regionId) {
        if (!available()) {
            return Optional.empty();
        }
        Key key = new Key(world, regionId);
        long now = System.nanoTime();
        Cached current = cache.get(key);
        if (current != null && now - current.createdAt() < cacheNanos) {
            return current.shape();
        }
        Optional<HorizontalShape> shape = readShape(world, regionId);
        cache.put(key, new Cached(now, shape));
        return shape;
    }

    private Optional<HorizontalShape> readShape(String world, String regionId) {
        RegionManager manager = manager(world);
        var region = manager == null ? null : manager.getRegion(regionId);
        if (region == null || !region.isPhysicalArea()) {
            return Optional.empty();
        }
        if (region instanceof ProtectedCuboidRegion) {
            var min = region.getMinimumPoint();
            var max = region.getMaximumPoint();
            return Optional.of(new HorizontalShape.Box(
                    min.getBlockX(), min.getBlockZ(), max.getBlockX() + 1.0, max.getBlockZ() + 1.0));
        }
        List<HorizontalShape.Point> vertices = region.getPoints().stream()
                .map(point -> new HorizontalShape.Point(point.getBlockX(), point.getBlockZ())).toList();
        return vertices.size() < 3 ? Optional.empty() : Optional.of(new HorizontalShape.Polygon(vertices));
    }

    @Override
    public List<String> ids(String world) {
        RegionManager manager = available() ? manager(world) : null;
        return manager == null ? List.of() : manager.getRegions().values().stream()
                .filter(region -> region.isPhysicalArea()).map(region -> region.getId()).sorted().toList();
    }

    @Override
    public void invalidate() {
        cache.clear();
    }

    private static RegionManager manager(String name) {
        var world = Bukkit.getWorld(name);
        return world == null ? null : WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
    }

    private record Key(String world, String regionId) { }
    private record Cached(long createdAt, Optional<HorizontalShape> shape) { }
}
