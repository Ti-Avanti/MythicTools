package gg.fotia.mythictools.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BossManagerLifecycleTest {
    @Test
    void sweepForExternallyRemovedBossCleansEveryIndexWithoutSettlement() {
        BossFightRegistry registry = new BossFightRegistry();
        UUID entityId = UUID.randomUUID();
        BossFight fight = new BossFight(bossConfig(), "boss:point", entityId);
        fight.record(UUID.randomUUID(), "Tester", 10.0);
        registry.add(fight);
        assertEquals(1, registry.size());

        int removed = registry.sweep(id -> false);

        assertEquals(1, removed);
        assertEquals(0, registry.size());
        assertEquals(0, registry.bossCount("boss"));
        assertEquals(0, registry.spawnerCount("boss:point"));
        assertEquals(null, registry.entity(entityId));
        assertEquals(10.0, fight.damage.values().iterator().next());
    }

    private static BossConfig bossConfig() {
        BossBroadcast empty = new BossBroadcast(Map.of(), List.of());
        return new BossConfig(
                "boss", Map.of("zh_CN", "Boss"), BossPhaseMode.DEATH_RESPAWN,
                List.of(new BossPhase("TestBoss", 1.0)), null, BossLootPolicy.legacy(), List.of(),
                empty, empty, new BossRewardConfig(false, 1, Map.of(), false, false, List.of()));
    }
}
