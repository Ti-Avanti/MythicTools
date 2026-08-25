package gg.fotia.mythictools.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BossPhaseModeTest {

    @Test
    void parsesBothSupportedStageModes() {
        assertEquals(BossPhaseMode.DEATH_RESPAWN, BossPhaseMode.fromConfig("death-respawn"));
        assertEquals(BossPhaseMode.MYTHIC_NATIVE, BossPhaseMode.fromConfig("mythic-native"));
        assertTrue(BossPhaseMode.DEATH_RESPAWN.replacesEntityOnStageDeath());
        assertFalse(BossPhaseMode.MYTHIC_NATIVE.replacesEntityOnStageDeath());
    }

    @Test
    void rejectsUnknownStageMode() {
        assertThrows(IllegalArgumentException.class, () -> BossPhaseMode.fromConfig("health-swap"));
    }
}
