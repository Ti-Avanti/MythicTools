package gg.fotia.mythictools.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SafetyLimitsTest {

    @Test
    void loadsDocumentedDefaultsWhenSectionIsMissing() {
        SafetyLimits limits = SafetyLimits.load(new YamlConfiguration());

        assertEquals(16, limits.maxDropsPerMob());
        assertEquals(2304, limits.maxItemAmountPerGrant());
        assertEquals(16, limits.maxCommandExecutionsPerGrant());
        assertEquals(100_000, limits.maxExperiencePerMob());
        assertEquals(1_000_000L, limits.maxWeight());
        assertEquals(16, limits.maxBossGroupCopies());
        assertEquals(32, limits.maxBossSelectionsPerRecipient());
        assertEquals(20, limits.maxBossRecipients());
    }

    @Test
    void loadsCustomPositiveIntegralLimits() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Safety-Limits.Max-Drops-Per-Mob", 7);
        yaml.set("Safety-Limits.Max-Item-Amount-Per-Grant", 64);
        yaml.set("Safety-Limits.Max-Command-Executions-Per-Grant", 3);
        yaml.set("Safety-Limits.Max-Experience-Per-Mob", 500);
        yaml.set("Safety-Limits.Max-Weight", 4_000_000_000L);
        yaml.set("Safety-Limits.Max-Boss-Group-Copies", 4);
        yaml.set("Safety-Limits.Max-Boss-Selections-Per-Recipient", 8);
        yaml.set("Safety-Limits.Max-Boss-Recipients", 2);

        SafetyLimits limits = SafetyLimits.load(yaml);

        assertEquals(7, limits.maxDropsPerMob());
        assertEquals(4_000_000_000L, limits.maxWeight());
        assertEquals(2, limits.maxBossRecipients());
    }

    @Test
    void rejectsZeroNegativeAndFloatingPointLimits() {
        YamlConfiguration zero = new YamlConfiguration();
        zero.set("Safety-Limits.Max-Drops-Per-Mob", 0);
        assertThrows(IllegalArgumentException.class, () -> SafetyLimits.load(zero));

        YamlConfiguration negative = new YamlConfiguration();
        negative.set("Safety-Limits.Max-Weight", -1L);
        assertThrows(IllegalArgumentException.class, () -> SafetyLimits.load(negative));

        YamlConfiguration floating = new YamlConfiguration();
        floating.set("Safety-Limits.Max-Boss-Recipients", 2.5D);
        assertThrows(IllegalArgumentException.class, () -> SafetyLimits.load(floating));
    }

    @Test
    void pluginSettingsRejectsInvalidMainSpawningNumbersInsteadOfClamping() {
        YamlConfiguration zeroDistance = new YamlConfiguration();
        zeroDistance.set("Spawning.Default-Min-Distance", 0);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.load(zeroDistance));

        YamlConfiguration descending = new YamlConfiguration();
        descending.set("Spawning.Default-Min-Distance", 32);
        descending.set("Spawning.Default-Max-Distance", 12);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.load(descending));

        YamlConfiguration fractional = new YamlConfiguration();
        fractional.set("Spawning.Location-Attempts", 2.5D);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.load(fractional));
    }
}
