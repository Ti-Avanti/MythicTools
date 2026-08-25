package gg.fotia.mythictools.boss;

import java.util.Set;
import org.bukkit.Location;
import org.bukkit.block.Biome;

/** Boss 群系随机或固定位置生成器。 */
public record BossSpawner(
        String id,
        BossSpawnType type,
        boolean enabled,
        long intervalSeconds,
        double chance,
        int maxActive,
        int minimumOnlinePlayers,
        BossPointSchedule pointSchedule,
        Set<String> worlds,
        Set<Biome> biomes,
        int minDistance,
        int maxDistance,
        Location location) {
    public BossSpawner {
        if (minimumOnlinePlayers < 0) {
            throw new IllegalArgumentException("最低在线人数不能小于 0");
        }
        if (type == BossSpawnType.POINT && pointSchedule == null) {
            throw new IllegalArgumentException("固定 Boss 点必须配置刷新计划");
        }
        if (type == BossSpawnType.BIOME && pointSchedule != null) {
            throw new IllegalArgumentException("群系 Boss 生成器不能配置固定点刷新计划");
        }
        worlds = Set.copyOf(worlds);
        biomes = Set.copyOf(biomes);
        location = location == null ? null : location.clone();
    }

    @Override
    public Location location() {
        return location == null ? null : location.clone();
    }
}
