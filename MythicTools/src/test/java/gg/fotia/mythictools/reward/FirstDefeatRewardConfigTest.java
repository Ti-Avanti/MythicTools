package gg.fotia.mythictools.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class FirstDefeatRewardConfigTest {
    @Test
    void keepsConfiguredExactRewardReferences() {
        FirstDefeatRewardConfig config = new FirstDefeatRewardConfig(
                true, FirstDefeatScope.PLAYER, FirstDefeatRecipient.PARTICIPANTS,
                List.of(new RewardEntryRef("first-clear", "trophy")));

        assertEquals("trophy", config.entries().get(0).entryId());
    }

    @Test
    void rejectsServerWideParticipantFanout() {
        assertThrows(IllegalArgumentException.class, () -> new FirstDefeatRewardConfig(
                true, FirstDefeatScope.SERVER, FirstDefeatRecipient.PARTICIPANTS,
                List.of(new RewardEntryRef("first-clear", "trophy"))));
    }

    @Test
    void rejectsEnabledConfigurationWithoutRewards() {
        assertThrows(IllegalArgumentException.class, () -> new FirstDefeatRewardConfig(
                true, FirstDefeatScope.PLAYER, FirstDefeatRecipient.KILLER, List.of()));
    }
}
