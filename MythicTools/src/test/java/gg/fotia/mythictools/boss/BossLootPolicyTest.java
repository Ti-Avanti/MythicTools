package gg.fotia.mythictools.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BossLootPolicyTest {

    @Test
    void clearsIntermediateMythicDropsWhenConfiguredAsNone() {
        BossLootPolicy policy = new BossLootPolicy(
                IntermediateStageLootMode.NONE, FinalStageLootMode.MYTHICTOOLS_ONLY);

        assertEquals(BossDropAction.CLEAR_MYTHIC_DROPS, policy.dropAction(false));
        assertEquals(BossDropAction.CLEAR_MYTHIC_DROPS, policy.dropAction(true));
        assertTrue(policy.awardsMythicToolsRewardsOnFinalDeath());
    }

    @Test
    void preservesMythicDropsAndSuppressesPluginRewardsForMythicOnlyFinalDeath() {
        BossLootPolicy policy = new BossLootPolicy(
                IntermediateStageLootMode.MYTHIC, FinalStageLootMode.MYTHIC_ONLY);

        assertEquals(BossDropAction.KEEP_MYTHIC_DROPS, policy.dropAction(false));
        assertEquals(BossDropAction.KEEP_MYTHIC_DROPS, policy.dropAction(true));
        assertFalse(policy.awardsMythicToolsRewardsOnFinalDeath());
    }

    @Test
    void retainsLegacyBehaviorForExistingBossFiles() {
        BossLootPolicy policy = BossLootPolicy.legacy();

        assertEquals(BossDropAction.LEGACY, policy.dropAction(false));
        assertEquals(BossDropAction.LEGACY, policy.dropAction(true));
        assertTrue(policy.awardsMythicToolsRewardsOnFinalDeath());
    }
}
