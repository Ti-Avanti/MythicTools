package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BossRewardSelectionRulesTest {
    @Test
    void copiesMustStayInsideConfiguredRange() {
        assertDoesNotThrow(() -> BossRewardSelectionRules.requireCopies(1, 16));
        assertDoesNotThrow(() -> BossRewardSelectionRules.requireCopies(16, 16));
        assertThrows(IllegalArgumentException.class,
                () -> BossRewardSelectionRules.requireCopies(0, 16));
        assertThrows(IllegalArgumentException.class,
                () -> BossRewardSelectionRules.requireCopies(17, 16));
    }

    @Test
    void recipientTotalCannotExceedConfiguredLimit() {
        assertDoesNotThrow(() -> BossRewardSelectionRules.requireTotal(List.of(8, 8), 16));
        assertThrows(IllegalArgumentException.class,
                () -> BossRewardSelectionRules.requireTotal(List.of(9, 8), 16));
    }

    @Test
    void enabledRankingAndKillerRewardsShareTheRecipientLimit() {
        assertDoesNotThrow(() -> BossRewardSelectionRules.requireCombined(
                true, List.of(List.of(10), List.of(3)), true, List.of(6), 16));
        assertThrows(IllegalArgumentException.class,
                () -> BossRewardSelectionRules.requireCombined(
                        true, List.of(List.of(11), List.of(3)), true, List.of(6), 16));
    }

    @Test
    void disabledRewardSourceDoesNotConsumeCombinedLimit() {
        assertDoesNotThrow(() -> BossRewardSelectionRules.requireCombined(
                false, List.of(List.of(16)), true, List.of(16), 16));
        assertDoesNotThrow(() -> BossRewardSelectionRules.requireCombined(
                true, List.of(List.of(16)), false, List.of(16), 16));
    }

    public static void main(String[] args) {
        BossRewardSelectionRulesTest test = new BossRewardSelectionRulesTest();
        test.copiesMustStayInsideConfiguredRange();
        test.recipientTotalCannotExceedConfiguredLimit();
        test.enabledRankingAndKillerRewardsShareTheRecipientLimit();
        test.disabledRewardSourceDoesNotConsumeCombinedLimit();
    }
}
