package gg.fotia.mythictools.blackboxtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TestEntityCleanupTest {
    @Test
    void removesBothMythicRegistrationAndPhysicalEntity() {
        AtomicInteger mythicRemovals = new AtomicInteger();
        AtomicInteger physicalRemovals = new AtomicInteger();

        TestEntityCleanup.remove(mythicRemovals::incrementAndGet, physicalRemovals::incrementAndGet);

        assertEquals(1, mythicRemovals.get());
        assertEquals(1, physicalRemovals.get());
    }

    @Test
    void stillRemovesPhysicalEntityWhenMythicRemovalFails() {
        AtomicInteger physicalRemovals = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> TestEntityCleanup.remove(
                () -> {
                    throw new IllegalStateException("already detached");
                },
                physicalRemovals::incrementAndGet));

        assertEquals(1, physicalRemovals.get());
    }
}
