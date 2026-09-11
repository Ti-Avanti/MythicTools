package gg.fotia.mythictools.spawning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/** 单个 tick 内按世界、区块复用实体位置快照，生成后立即补入索引。 */
final class NearbyMobIndex {
    private final Map<UUID, Map<Long, List<Location>>> worlds = new HashMap<>();

    NearbyMobIndex(Collection<UUID> entities) {
        for (UUID id : entities) {
            var entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid() && !entity.isDead()) {
                add(entity.getLocation());
            }
        }
    }

    void add(Location location) {
        worlds.computeIfAbsent(location.getWorld().getUID(), ignored -> new HashMap<>())
                .computeIfAbsent(key(location.getBlockX() >> 4, location.getBlockZ() >> 4),
                        ignored -> new ArrayList<>()).add(location.clone());
    }

    int count(Location origin, int radius, int stopAt) {
        Map<Long, List<Location>> chunks = worlds.get(origin.getWorld().getUID());
        if (chunks == null || stopAt <= 0) {
            return 0;
        }
        double squared = (double) radius * radius;
        int minX = (int) Math.floor((origin.getX() - radius) / 16.0);
        int maxX = (int) Math.floor((origin.getX() + radius) / 16.0);
        int minZ = (int) Math.floor((origin.getZ() - radius) / 16.0);
        int maxZ = (int) Math.floor((origin.getZ() + radius) / 16.0);
        int count = 0;
        // 超大半径只遍历已存在的桶，避免按半径枚举巨量空区块。
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > chunks.size() * 4L) {
            for (List<Location> locations : chunks.values()) {
                count += count(locations, origin, squared, stopAt - count);
                if (count >= stopAt) {
                    return count;
                }
            }
        } else {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    count += count(chunks.getOrDefault(key(x, z), List.of()), origin, squared, stopAt - count);
                    if (count >= stopAt) {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    private static int count(List<Location> locations, Location origin, double squared, int stopAt) {
        int count = 0;
        for (Location location : locations) {
            if (location.distanceSquared(origin) <= squared && ++count >= stopAt) {
                break;
            }
        }
        return count;
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }
}
