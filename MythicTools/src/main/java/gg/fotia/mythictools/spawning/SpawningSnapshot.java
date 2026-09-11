package gg.fotia.mythictools.spawning;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 刷怪配置的不可变快照。 */
public final class SpawningSnapshot {
    private final Map<String, BiomeSpawnRule> biomeRules;
    private final Map<String, SpawnPoint> spawnPoints;
    private final Map<String, MobGroup> mobGroups;
    private final BiomeRuleIndex biomeIndex;

    public SpawningSnapshot(
            Map<String, BiomeSpawnRule> biomeRules,
            Map<String, SpawnPoint> spawnPoints,
            Map<String, MobGroup> mobGroups) {
        this.biomeRules = immutableCopy(biomeRules, SpawningSnapshot::copyBiomeRule);
        this.spawnPoints = immutableCopy(spawnPoints, SpawningSnapshot::copySpawnPoint);
        this.mobGroups = immutableCopy(mobGroups, SpawningSnapshot::copyMobGroup);
        this.biomeIndex = new BiomeRuleIndex(this.biomeRules.values());
    }

    public static SpawningSnapshot empty() {
        return new SpawningSnapshot(Map.of(), Map.of(), Map.of());
    }

    public Collection<BiomeSpawnRule> biomeRules() {
        return biomeRules.values();
    }

    public Collection<BiomeSpawnRule> biomeRules(String world, org.bukkit.block.Biome biome) {
        return biomeIndex.matching(world, biome);
    }

    public Collection<SpawnPoint> spawnPoints() {
        return spawnPoints.values();
    }

    public Collection<String> biomeRuleIds() {
        return List.copyOf(biomeRules.keySet());
    }

    public Collection<String> spawnPointIds() {
        return List.copyOf(spawnPoints.keySet());
    }

    public Collection<String> mobGroupIds() {
        return List.copyOf(mobGroups.keySet());
    }

    public SpawnPoint spawnPoint(String id) {
        return spawnPoints.get(id);
    }

    public MobGroup mobGroup(String id) {
        return mobGroups.get(id);
    }

    private static BiomeSpawnRule copyBiomeRule(BiomeSpawnRule value) {
        return new BiomeSpawnRule(
                value.id(), value.mobId(), value.mobGroupId(), value.worlds(), value.biomes(), value.chance(),
                value.intervalSeconds(), value.minDistance(), value.maxDistance(), value.minY(), value.maxY(),
                value.minLight(), value.maxLight(), value.minAmount(), value.maxAmount(), value.maxAliveNearby(),
                value.maxAliveGlobal(), value.nearbyRadius(), value.level(), value.despawnSeconds(), value.enabled());
    }

    private static SpawnPoint copySpawnPoint(SpawnPoint value) {
        return new SpawnPoint(
                value.id(), value.mobId(), value.mobGroupId(), value.location(), value.intervalSeconds(),
                value.minAmount(), value.maxAmount(), value.maxAlive(), value.level(), value.spawnOnDeath(),
                value.despawnSeconds(), value.enabled());
    }

    private static MobGroup copyMobGroup(MobGroup value) {
        return new MobGroup(value.id(), value.minAmount(), value.maxAmount(), value.members());
    }

    private static <T> Map<String, T> immutableCopy(
            Map<String, T> source,
            java.util.function.Function<T, T> copier) {
        Map<String, T> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, copier.apply(value)));
        return Collections.unmodifiableMap(result);
    }
}
