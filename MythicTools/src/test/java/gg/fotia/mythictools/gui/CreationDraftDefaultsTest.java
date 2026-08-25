package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CreationDraftDefaultsTest {
    @Test
    void prefersLoadedMobThenFallsBackToLoadedMobGroup() {
        CreationDraftDefaults.SpawnSource mob = CreationDraftDefaults.spawnSource(
                List.of("ActualMob"), List.of("actual-group"));
        assertEquals("ActualMob", mob.mobId());
        assertNull(mob.mobGroupId());

        CreationDraftDefaults.SpawnSource group = CreationDraftDefaults.spawnSource(
                List.of(), List.of("actual-group"));
        assertNull(group.mobId());
        assertEquals("actual-group", group.mobGroupId());
    }

    @Test
    void rejectsSpawnDraftWhenNoLoadableSourceExists() {
        assertThrows(IllegalStateException.class,
                () -> CreationDraftDefaults.spawnSource(List.of(), List.of()));
        assertThrows(IllegalStateException.class,
                () -> CreationDraftDefaults.requireMob(List.of(), "Boss"));
    }

    @Test
    void selectsOnlyActuallyLoadedDropGroupOrLeavesRewardsDisabled() {
        assertEquals("real-rewards", CreationDraftDefaults.firstId(List.of("", "real-rewards")));
        assertNull(CreationDraftDefaults.firstId(List.of()));
    }
}
