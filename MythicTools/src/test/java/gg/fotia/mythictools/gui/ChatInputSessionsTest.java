package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChatInputSessionsTest {
    @Test
    void takeConsumesSessionExactlyOnce() {
        ChatInputSessions sessions = new ChatInputSessions();
        UUID player = UUID.randomUUID();
        sessions.put(player, value -> { }, () -> { });

        assertTrue(sessions.take(player).isPresent());
        assertFalse(sessions.take(player).isPresent());
        assertFalse(sessions.isAwaiting(player));
    }

    @Test
    void discardAndDiscardAllNeverExecuteCallbacks() {
        ChatInputSessions sessions = new ChatInputSessions();
        AtomicInteger callbacks = new AtomicInteger();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        sessions.put(first, value -> callbacks.incrementAndGet(), callbacks::incrementAndGet);
        sessions.put(second, value -> callbacks.incrementAndGet(), callbacks::incrementAndGet);

        sessions.discard(first);
        sessions.discardAll();

        assertEquals(0, callbacks.get());
        assertFalse(sessions.isAwaiting(first));
        assertFalse(sessions.isAwaiting(second));
    }

    @Test
    void takenSessionIsInvalidatedWhenAReplacementStarts() {
        ChatInputSessions sessions = new ChatInputSessions();
        UUID player = UUID.randomUUID();
        sessions.put(player, value -> { }, () -> { });
        ChatInputSessions.Session taken = sessions.take(player).orElseThrow();

        sessions.put(player, value -> { }, () -> { });

        assertFalse(sessions.isCurrent(player, taken));
        assertTrue(sessions.isAwaiting(player));
    }

    @Test
    void discardInvalidatesAQueuedTakenSession() {
        ChatInputSessions sessions = new ChatInputSessions();
        UUID player = UUID.randomUUID();
        sessions.put(player, value -> { }, () -> { });
        ChatInputSessions.Session taken = sessions.take(player).orElseThrow();

        sessions.discard(player);

        assertFalse(sessions.isCurrent(player, taken));
    }

    @Test
    void completingOldSessionNeverClearsReplacement() {
        ChatInputSessions sessions = new ChatInputSessions();
        UUID player = UUID.randomUUID();
        sessions.put(player, value -> { }, () -> { });
        ChatInputSessions.Session old = sessions.take(player).orElseThrow();
        sessions.put(player, value -> { }, () -> { });

        sessions.complete(player, old);

        assertTrue(sessions.isAwaiting(player));
    }
}
