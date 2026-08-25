package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gg.fotia.mythictools.config.SafetyLimits;
import org.junit.jupiter.api.Test;

class GuiWeightRulesTest {

    @Test
    void acceptsConfiguredBoundaryAndRejectsOverLimit() {
        SafetyLimits limits = new SafetyLimits(16, 2304, 16, 100_000,
                4_000_000_000L, 16, 32, 20);

        assertEquals(4_000_000_000L, GuiWeightRules.requireValid(4_000_000_000L, limits));
        assertThrows(IllegalArgumentException.class,
                () -> GuiWeightRules.requireValid(4_000_000_001L, limits));
        assertThrows(IllegalArgumentException.class,
                () -> GuiWeightRules.requireValid(0L, limits));
    }
}
