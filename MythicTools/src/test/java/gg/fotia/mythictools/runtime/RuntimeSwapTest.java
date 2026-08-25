package gg.fotia.mythictools.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeSwapTest {
    @Test
    void activationFailureKeepsOldRuntimeAndCleansCandidate() {
        List<String> events = new ArrayList<>();
        RuntimeSwap<FakeRuntime> swap = new RuntimeSwap<>(failure -> events.add("reported"));
        FakeRuntime oldRuntime = new FakeRuntime("old", events, false, false);
        swap.installInitial(oldRuntime);
        FakeRuntime candidate = new FakeRuntime("candidate", events, true, false);

        assertThrows(IllegalStateException.class, () -> swap.replace(() -> candidate));

        assertSame(oldRuntime, swap.current());
        assertEquals(List.of("old.activate", "candidate.activate", "candidate.close"), events);
    }

    @Test
    void preparationFailureKeepsOldRuntimeUntouched() {
        List<String> events = new ArrayList<>();
        RuntimeSwap<FakeRuntime> swap = new RuntimeSwap<>(failure -> events.add("reported"));
        FakeRuntime oldRuntime = new FakeRuntime("old", events, false, false);
        swap.installInitial(oldRuntime);

        assertThrows(IllegalArgumentException.class, () -> swap.replace(() -> {
            throw new IllegalArgumentException("invalid configuration");
        }));

        assertSame(oldRuntime, swap.current());
        assertEquals(List.of("old.activate"), events);
    }

    @Test
    void commitsCandidateBeforeClosingOldRuntime() {
        List<String> events = new ArrayList<>();
        RuntimeSwap<FakeRuntime> swap = new RuntimeSwap<>(failure -> events.add("reported"));
        FakeRuntime oldRuntime = new FakeRuntime("old", events, false, false);
        swap.installInitial(oldRuntime);
        FakeRuntime candidate = new FakeRuntime("candidate", events, false, false);
        oldRuntime.onClose = () -> assertSame(candidate, swap.current());

        swap.replace(() -> candidate);

        assertSame(candidate, swap.current());
        assertEquals(List.of("old.activate", "candidate.activate", "old.close"), events);
    }

    @Test
    void oldCleanupFailureDoesNotRollBackNewRuntime() {
        List<String> events = new ArrayList<>();
        RuntimeSwap<FakeRuntime> swap = new RuntimeSwap<>(failure -> events.add("reported"));
        FakeRuntime oldRuntime = new FakeRuntime("old", events, false, true);
        swap.installInitial(oldRuntime);
        FakeRuntime candidate = new FakeRuntime("candidate", events, false, false);

        swap.replace(() -> candidate);

        assertSame(candidate, swap.current());
        assertEquals(List.of("old.activate", "candidate.activate", "old.close", "reported"), events);
    }

    @Test
    void shutdownIsIdempotent() {
        List<String> events = new ArrayList<>();
        RuntimeSwap<FakeRuntime> swap = new RuntimeSwap<>(failure -> events.add("reported"));
        swap.installInitial(new FakeRuntime("runtime", events, false, false));

        swap.close();
        swap.close();

        assertEquals(List.of("runtime.activate", "runtime.close"), events);
    }

    private static final class FakeRuntime implements ManagedRuntime {
        private final String name;
        private final List<String> events;
        private final boolean failActivation;
        private final boolean failClose;
        private boolean closed;
        private Runnable onClose = () -> { };

        private FakeRuntime(String name, List<String> events, boolean failActivation, boolean failClose) {
            this.name = name;
            this.events = events;
            this.failActivation = failActivation;
            this.failClose = failClose;
        }

        @Override
        public void activate() {
            events.add(name + ".activate");
            if (failActivation) {
                throw new IllegalStateException("activation failed");
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            events.add(name + ".close");
            onClose.run();
            if (failClose) {
                throw new IllegalStateException("close failed");
            }
        }
    }
}
