package gg.fotia.mythictools.blackboxtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProbeStateTest {
    @Test
    void recordsFixtureAndFailedReloadRollbackEvidence() {
        ProbeState state = new ProbeState();
        state.activateFixture("invalid-reload");
        state.recordReload(false, "points=qa-point;bosses=qa-boss", "points=qa-point;bosses=qa-boss", "bad config");

        assertEquals("invalid-reload", state.activeFixture());
        assertFalse(state.lastReload().success());
        assertTrue(state.lastReload().preserved());
        assertEquals("bad config", state.lastReload().reason());
    }

    @Test
    void clearReturnsStateToMachineFriendlyDefaults() {
        ProbeState state = new ProbeState();
        state.activateFixture("qa-point");
        state.recordReload(true, "old", "new", "");

        state.clear();

        assertEquals("none", state.activeFixture());
        assertEquals("never", state.lastReload().status());
    }
}
