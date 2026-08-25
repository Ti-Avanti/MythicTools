package gg.fotia.mythictools.boss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** 同步维护 Boss 实体、Boss ID 与生成器三套索引。 */
final class BossFightRegistry {
    private final Map<UUID, BossFight> byEntity = new HashMap<>();
    private final Map<String, Set<BossFight>> byBoss = new HashMap<>();
    private final Map<String, Set<BossFight>> bySpawner = new HashMap<>();

    void add(BossFight fight) {
        bindEntity(fight);
        byBoss.computeIfAbsent(fight.config.id(), ignored -> new HashSet<>()).add(fight);
        bySpawner.computeIfAbsent(fight.spawnerKey, ignored -> new HashSet<>()).add(fight);
    }

    void bindEntity(BossFight fight) {
        byEntity.put(fight.entityId, fight);
    }

    BossFight detachEntity(UUID entityId) {
        return byEntity.remove(entityId);
    }

    BossFight entity(UUID entityId) {
        return byEntity.get(entityId);
    }

    boolean containsEntity(UUID entityId) {
        return byEntity.containsKey(entityId);
    }

    int bossCount(String bossId) {
        return byBoss.getOrDefault(bossId, Set.of()).size();
    }

    int spawnerCount(String spawnerKey) {
        return bySpawner.getOrDefault(spawnerKey, Set.of()).size();
    }

    int size() {
        return byBoss.values().stream().mapToInt(Set::size).sum();
    }

    Optional<BossFight> firstBoss(String bossId) {
        return byBoss.getOrDefault(bossId, Set.of()).stream().findFirst();
    }

    List<UUID> entityIds() {
        return List.copyOf(byEntity.keySet());
    }

    void remove(BossFight fight) {
        byEntity.remove(fight.entityId);
        removeFromSet(byBoss, fight.config.id(), fight);
        removeFromSet(bySpawner, fight.spawnerKey, fight);
    }

    int sweep(Predicate<UUID> active) {
        int removed = 0;
        for (BossFight fight : List.copyOf(byEntity.values())) {
            if (!active.test(fight.entityId)) {
                remove(fight);
                removed++;
            }
        }
        return removed;
    }

    void clear() {
        byEntity.clear();
        byBoss.clear();
        bySpawner.clear();
    }

    private static void removeFromSet(Map<String, Set<BossFight>> index, String key, BossFight fight) {
        Set<BossFight> fights = index.get(key);
        if (fights != null) {
            fights.remove(fight);
            if (fights.isEmpty()) {
                index.remove(key);
            }
        }
    }
}
