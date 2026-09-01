package gg.fotia.mythictools.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.fotia.mythictools.reward.FirstDefeatRecipient;
import gg.fotia.mythictools.reward.FirstDefeatScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BossFirstDefeatRecipientsTest {
    @Test
    void ordersParticipantsByDamageAndIncludesTheKiller() {
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        UUID killer = UUID.randomUUID();
        Map<UUID, Double> damage = new LinkedHashMap<>();
        damage.put(low, 5.0);
        damage.put(high, 20.0);

        assertEquals(List.of(high, low, killer), BossFirstDefeatRecipients.select(
                FirstDefeatScope.PLAYER, FirstDefeatRecipient.PARTICIPANTS, damage, killer));
    }

    @Test
    void serverScopeAlwaysTargetsOnlyTheKiller() {
        UUID participant = UUID.randomUUID();
        UUID killer = UUID.randomUUID();

        assertEquals(List.of(killer), BossFirstDefeatRecipients.select(
                FirstDefeatScope.SERVER, FirstDefeatRecipient.KILLER,
                Map.of(participant, 100.0), killer));
    }
}
