package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.fotia.mythictools.reward.RewardEntryRef;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class FirstDefeatSettingsTest {
    @Test
    void usesDifferentRootsForBossAndMobConfigurations() {
        assertEquals("rewards.first-defeat", FirstDefeatSettings.root(AdminType.BOSS));
        assertEquals("first-defeat", FirstDefeatSettings.root(AdminType.MOB_DROP));
    }

    @Test
    void addsExactReferencesOnlyOnceAndRemovesThem() {
        YamlConfiguration yaml = new YamlConfiguration();
        RewardEntryRef reward = new RewardEntryRef("first-clear", "trophy");

        FirstDefeatSettings.add(yaml, AdminType.BOSS, reward);
        FirstDefeatSettings.add(yaml, AdminType.BOSS, reward);

        assertEquals(1, FirstDefeatSettings.entries(yaml, AdminType.BOSS).size());
        FirstDefeatSettings.remove(yaml, AdminType.BOSS, reward);
        assertTrue(FirstDefeatSettings.entries(yaml, AdminType.BOSS).isEmpty());
    }

    @Test
    void serverScopeForcesKillerRecipient() {
        YamlConfiguration yaml = new YamlConfiguration();
        FirstDefeatSettings.ensureDefaults(yaml, AdminType.BOSS);
        FirstDefeatSettings.setRecipient(yaml, AdminType.BOSS, "participants");

        FirstDefeatSettings.setScope(yaml, AdminType.BOSS, "server");

        assertEquals("server", FirstDefeatSettings.scope(yaml, AdminType.BOSS));
        assertEquals("killer", FirstDefeatSettings.recipient(yaml, AdminType.BOSS));
        assertFalse(FirstDefeatSettings.enabled(yaml, AdminType.BOSS));
    }
}
