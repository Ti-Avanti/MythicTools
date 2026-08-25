package gg.fotia.mythictools.spawning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.fotia.mythictools.integration.MythicMobGateway;
import gg.fotia.mythictools.runtime.OwnedTasks;
import gg.fotia.mythictools.runtime.TaskHandle;
import gg.fotia.mythictools.runtime.TaskScheduler;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class SpawningManagerLifecycleTest {
    @Test
    void pollingRemovesExternallyMissingMobFromAliveCount() {
        Fixture fixture = new Fixture();
        when(fixture.gateway.isLoadedAndActive(fixture.entityId)).thenReturn(true, false);

        assertTrue(fixture.manager.triggerPoint("point"));
        assertEquals(1, fixture.manager.aliveAtPoint("point"));
        assertEquals(0, fixture.manager.aliveAtPoint("point"));
    }

    @Test
    void stopCancelsOnlyOwnedTasksAndRemovesTrackedMobs() {
        Fixture fixture = new Fixture();
        when(fixture.gateway.activeMob(fixture.entityId)).thenReturn(Optional.of(fixture.activeMob));
        when(fixture.gateway.isLoadedAndActive(fixture.entityId)).thenReturn(true);
        fixture.manager.start();
        assertTrue(fixture.manager.triggerPoint("point"));
        assertEquals(1, fixture.manager.trackedEntityCount());

        fixture.manager.stop();

        verify(fixture.activeMob).remove();
        assertTrue(fixture.scheduler.tasks.stream().allMatch(task -> task.cancelled));
        assertEquals(0, fixture.manager.aliveAtPoint("point"));
        assertEquals(0, fixture.manager.trackedEntityCount());
    }

    @Test
    void triggerReportsFailureAndKeepsPointImmediatelyRetryableWhenGatewayDoesNotSpawn() {
        Fixture fixture = new Fixture();
        when(fixture.gateway.spawn("TestMob", fixture.location, 1.0)).thenReturn(Optional.empty());

        assertFalse(fixture.manager.triggerPoint("point"));
        assertEquals(-1L, fixture.manager.secondsUntilNext("point"));
        assertEquals(0, fixture.manager.aliveAtPoint("point"));
    }

    private static final class Fixture {
        private final SpawningConfigView repository = mock(SpawningConfigView.class);
        private final MythicMobGateway gateway = mock(MythicMobGateway.class);
        private final SpawnLocationFinder finder = new SpawnLocationFinder(1);
        private final FakeScheduler scheduler = new FakeScheduler();
        private final UUID entityId = UUID.randomUUID();
        private final ActiveMob activeMob = mock(ActiveMob.class);
        private final Location location;
        private final SpawningManager manager;

        private Fixture() {
            World world = mock(World.class);
            when(world.isChunkLoaded(0, 0)).thenReturn(true);
            location = new Location(world, 0, 64, 0);
            SpawnPoint point = new SpawnPoint(
                    "point", "TestMob", null, location, 60L, 1, 1, 1, 1.0, false, 0L, true);
            when(repository.spawnPoint("point")).thenReturn(point);
            when(repository.spawnPoints()).thenReturn(List.of(point));
            when(repository.biomeRules()).thenReturn(List.of());
            when(activeMob.getUniqueId()).thenReturn(entityId);
            when(gateway.spawn("TestMob", location, 1.0)).thenReturn(Optional.of(activeMob));
            manager = new SpawningManager(
                    repository, gateway, finder, 20L, true, new OwnedTasks(scheduler));
        }
    }

    private static final class FakeScheduler implements TaskScheduler {
        private final List<FakeTask> tasks = new ArrayList<>();

        @Override
        public TaskHandle execute(Runnable command) {
            return add(command);
        }

        @Override
        public TaskHandle later(Runnable command, long delayTicks) {
            return add(command);
        }

        @Override
        public TaskHandle repeating(Runnable command, long delayTicks, long periodTicks) {
            return add(command);
        }

        private TaskHandle add(Runnable command) {
            FakeTask task = new FakeTask(command);
            tasks.add(task);
            return task;
        }
    }

    private static final class FakeTask implements TaskHandle {
        private final Runnable command;
        private boolean cancelled;

        private FakeTask(Runnable command) {
            this.command = command;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
