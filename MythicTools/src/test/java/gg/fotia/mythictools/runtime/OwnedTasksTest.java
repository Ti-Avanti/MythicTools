package gg.fotia.mythictools.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OwnedTasksTest {
    @Test
    void cancellationTargetsOnlyOwnedHandlesAndInvalidatesQueuedCallbacks() {
        FakeScheduler scheduler = new FakeScheduler();
        OwnedTasks first = new OwnedTasks(scheduler);
        OwnedTasks second = new OwnedTasks(scheduler);
        AtomicInteger calls = new AtomicInteger();

        first.execute(calls::incrementAndGet);
        first.later(calls::incrementAndGet, 20L);
        second.execute(calls::incrementAndGet);
        first.cancelAll();
        scheduler.runAll();

        assertEquals(1, calls.get());
        assertTrue(scheduler.tasks.get(0).cancelled);
        assertTrue(scheduler.tasks.get(1).cancelled);
        assertEquals(false, scheduler.tasks.get(2).cancelled);
    }

    @Test
    void newGenerationCannotRunCallbackFromPreviousGeneration() {
        FakeScheduler scheduler = new FakeScheduler();
        OwnedTasks tasks = new OwnedTasks(scheduler);
        AtomicInteger calls = new AtomicInteger();
        tasks.later(calls::incrementAndGet, 1L);

        tasks.beginGeneration();
        tasks.execute(calls::incrementAndGet);
        scheduler.runAll();

        assertEquals(1, calls.get());
    }

    @Test
    void completedOneShotIsReleasedFromOwnership() {
        FakeScheduler scheduler = new FakeScheduler();
        OwnedTasks tasks = new OwnedTasks(scheduler);
        tasks.execute(() -> { });
        FakeTask completed = scheduler.tasks.get(0);

        completed.run();
        tasks.cancelAll();

        assertEquals(false, completed.cancelled);
    }

    @Test
    void cancelledHandleCannotRunEvenWhenSchedulerRaces() {
        FakeScheduler scheduler = new FakeScheduler();
        OwnedTasks tasks = new OwnedTasks(scheduler);
        AtomicInteger calls = new AtomicInteger();
        TaskHandle handle = tasks.later(calls::incrementAndGet, 1L);

        handle.cancel();
        scheduler.tasks.get(0).run();

        assertEquals(0, calls.get());
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

        private FakeTask add(Runnable command) {
            FakeTask task = new FakeTask(command);
            tasks.add(task);
            return task;
        }

        private void runAll() {
            List.copyOf(tasks).forEach(FakeTask::run);
        }
    }

    private static final class FakeTask implements TaskHandle {
        private final Runnable command;
        private boolean cancelled;

        private FakeTask(Runnable command) {
            this.command = command;
        }

        private void run() {
            command.run();
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
