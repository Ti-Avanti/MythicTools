package gg.fotia.mythictools.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BossSpawnerTest {

    @Test
    void keepsOnlinePlayerRequirementsIndependentForPointAndBiomeSpawners() {
        BossSpawner point = new BossSpawner(
                "event-square", BossSpawnType.POINT, true, 7200L, 1.0, 1,
                5, BossPointSchedule.fixedInterval(7200L), Set.of(), Set.of(), 0, 0, null);
        BossSpawner biome = new BossSpawner(
                "forest", BossSpawnType.BIOME, true, 600L, 0.02, 1,
                12, null, Set.of("world"), Set.of(), 16, 40, null);

        assertEquals(5, point.minimumOnlinePlayers());
        assertEquals(12, biome.minimumOnlinePlayers());
        assertEquals(7200L, point.pointSchedule().fallbackIntervalSeconds());
    }
}
