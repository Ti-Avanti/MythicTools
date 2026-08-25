package gg.fotia.mythictools.spawning;

import java.util.Collection;

/** 刷怪运行时所需的只读配置视图。 */
public interface SpawningConfigView {
    Collection<BiomeSpawnRule> biomeRules();

    Collection<SpawnPoint> spawnPoints();

    SpawnPoint spawnPoint(String id);

    MobGroup mobGroup(String id);
}
