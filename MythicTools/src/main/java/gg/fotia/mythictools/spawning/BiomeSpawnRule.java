package gg.fotia.mythictools.spawning;

import java.util.Set;
import org.bukkit.block.Biome;

/** 玩家附近的 MythicMob 群系随机生成规则。 */
public record BiomeSpawnRule(
        String id,
        String mobId,
        String mobGroupId,
        Set<String> worlds,
        Set<Biome> biomes,
        double chance,
        long intervalSeconds,
        int minDistance,
        int maxDistance,
        int minY,
        int maxY,
        int minLight,
        int maxLight,
        int minAmount,
        int maxAmount,
        int maxAliveNearby,
        int maxAliveGlobal,
        int nearbyRadius,
        double level,
        long despawnSeconds,
        boolean enabled) {
    public BiomeSpawnRule {
        worlds = Set.copyOf(worlds);
        biomes = Set.copyOf(biomes);
    }

    public boolean usesMobGroup() {
        return mobGroupId != null;
    }
}
