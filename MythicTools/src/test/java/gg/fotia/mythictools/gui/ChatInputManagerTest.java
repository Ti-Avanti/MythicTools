package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.fotia.mythictools.runtime.OwnedTasks;
import gg.fotia.mythictools.runtime.TaskHandle;
import gg.fotia.mythictools.runtime.TaskScheduler;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ChatInputManagerTest {
    @Test
    void queuedCallbackDoesNotRunAfterSessionReplacement() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        ChatInputSessions sessions = new ChatInputSessions();
        QueuedScheduler scheduler = new QueuedScheduler();
        ChatInputManager manager = new ChatInputManager(
                null, null, sessions, new OwnedTasks(scheduler));
        AtomicInteger oldCallbacks = new AtomicInteger();

        sessions.put(playerId, ignored -> oldCallbacks.incrementAndGet(), () -> { });
        ChatInputSessions.Session old = sessions.take(playerId).orElseThrow();
        manager.queueInput(player, "old", old);
        sessions.put(playerId, ignored -> { }, () -> { });

        scheduler.runQueued();

        assertEquals(0, oldCallbacks.get());
        assertTrue(sessions.isAwaiting(playerId));
    }

    @Test
    void queuedCallbackDoesNotRunAfterPlayerLeaves() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(false);
        ChatInputSessions sessions = new ChatInputSessions();
        QueuedScheduler scheduler = new QueuedScheduler();
        ChatInputManager manager = new ChatInputManager(
                null, null, sessions, new OwnedTasks(scheduler));
        AtomicInteger callbacks = new AtomicInteger();

        sessions.put(playerId, ignored -> callbacks.incrementAndGet(), () -> { });
        ChatInputSessions.Session queued = sessions.take(playerId).orElseThrow();
        manager.queueInput(player, "value", queued);
        manager.discard(playerId);

        scheduler.runQueued();

        assertEquals(0, callbacks.get());
    }

    @Test
    void currentOnlineSessionRunsExactlyOnce() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        ChatInputSessions sessions = new ChatInputSessions();
        QueuedScheduler scheduler = new QueuedScheduler();
        ChatInputManager manager = new ChatInputManager(
                null, null, sessions, new OwnedTasks(scheduler));
        AtomicInteger callbacks = new AtomicInteger();

        sessions.put(playerId, ignored -> callbacks.incrementAndGet(), () -> { });
        ChatInputSessions.Session queued = sessions.take(playerId).orElseThrow();
        manager.queueInput(player, "value", queued);

        scheduler.runQueued();

        assertEquals(1, callbacks.get());
    }

    private static final class QueuedScheduler implements TaskScheduler {
        private Runnable queued;

        @Override
        public TaskHandle execute(Runnable command) {
            queued = command;
            return () -> queued = null;
        }

        @Override
        public TaskHandle later(Runnable command, long delayTicks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle repeating(Runnable command, long delayTicks, long periodTicks) {
            throw new UnsupportedOperationException();
        }

        void runQueued() {
            Runnable current = queued;
            queued = null;
            current.run();
        }
    }
}
