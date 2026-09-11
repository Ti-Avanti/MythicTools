package gg.fotia.mythictools.spawning;

import java.util.Collection;

/** 刷怪运行时所需的只读配置视图。 */
public interface SpawningConfigView {
    Collection<BiomeSpawnRule> biomeRules();

    default Collection<BiomeSpawnRule> biomeRules(String world, org.bukkit.block.Biome biome) {
        return biomeRules().stream().filter(rule -> rule.enabled() && rule.biomes().contains(biome)
                && (rule.worlds().isEmpty() || rule.worlds().contains(world))).toList();
    }

    default Object identity() {
        return this;
    }

    Collection<SpawnPoint> spawnPoints();

    SpawnPoint spawnPoint(String id);

    MobGroup mobGroup(String id);
}
