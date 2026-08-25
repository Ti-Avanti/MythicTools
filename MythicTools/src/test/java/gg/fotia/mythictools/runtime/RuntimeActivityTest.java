package gg.fotia.mythictools.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RuntimeActivityTest {
    @Test
    void idleRuntimeAllowsAtomicReplacement() {
        assertDoesNotThrow(() -> new RuntimeActivity(0, 0).requireIdle());
    }

    @Test
    void activeManagedEntitiesBlockDestructiveRuntimeReplacement() {
        ActiveRuntimeStateException exception = assertThrows(
                ActiveRuntimeStateException.class,
                () -> new RuntimeActivity(3, 2).requireIdle());

        assertEquals(3, exception.spawningEntities());
        assertEquals(2, exception.bossFights());
    }
}
