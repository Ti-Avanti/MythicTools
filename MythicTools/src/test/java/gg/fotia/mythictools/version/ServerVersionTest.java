package gg.fotia.mythictools.version;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerVersionTest {

    @Test
    void acceptsEveryRequestedPaperVersionRange() {
        assertSupported("1.20.1-R0.1-SNAPSHOT");
        assertSupported("1.20.4-R0.1-SNAPSHOT");
        assertSupported("1.21-R0.1-SNAPSHOT");
        assertSupported("1.21.1-R0.1-SNAPSHOT");
        assertSupported("1.21.11-R0.1-SNAPSHOT");
        assertSupported("26.1.2-R0.1-SNAPSHOT");
        assertSupported("26.1.2.build.64-stable");
        assertSupported("26.1.9-R0.1-SNAPSHOT");
        assertSupported("26.2-R0.1-SNAPSHOT");
        assertSupported("26.2.build.1-stable");
        assertSupported("26.2.7-R0.1-SNAPSHOT");
        assertSupported("26.3");
        assertSupported("26.3-R0.1-SNAPSHOT");
        assertSupported("26.3.build.151-beta");
        assertSupported("26.3.1.build.2-stable");
    }

    @Test
    void keepsAllPreviouslySupportedMinorVersions() {
        for (int patch = 0; patch <= 11; patch++) {
            assertSupported("1.21." + patch + "-R0.1-SNAPSHOT");
        }
        assertSupported("1.21.99-R0.1-SNAPSHOT");
        assertSupported("26.1.99.build.1-stable");
        assertSupported("26.2.99.build.1-stable");
    }

    @Test
    void rejectsVersionsOutsideTheDeclaredCompatibilityRange() {
        assertUnsupported("1.20.6-R0.1-SNAPSHOT");
        assertUnsupported("1.19.4-R0.1-SNAPSHOT");
        assertUnsupported("26.1.1-R0.1-SNAPSHOT");
        assertUnsupported("26.4.build.1-alpha");
        assertUnsupported("27.1.build.1-stable");
        assertUnsupported("not-a-version");
        assertUnsupported(null);
    }

    @Test
    void exposesModernItemVisualsOnlyOnServersThatProvideTheirApi() {
        assertFalse(ServerVersion.fromBukkitVersion("1.20.1-R0.1-SNAPSHOT").supportsItemModelAndTooltipStyle());
        assertFalse(ServerVersion.fromBukkitVersion("1.20.4-R0.1-SNAPSHOT").supportsItemModelAndTooltipStyle());
        assertFalse(ServerVersion.fromBukkitVersion("1.21.1-R0.1-SNAPSHOT").supportsItemModelAndTooltipStyle());

        assertTrue(ServerVersion.fromBukkitVersion("1.21.2-R0.1-SNAPSHOT").supportsItemModelAndTooltipStyle());
        assertTrue(ServerVersion.fromBukkitVersion("1.21.11-R0.1-SNAPSHOT").supportsItemModelAndTooltipStyle());
        assertTrue(ServerVersion.fromBukkitVersion("26.1.2.build.64-stable").supportsItemModelAndTooltipStyle());
        assertTrue(ServerVersion.fromBukkitVersion("26.2.build.1-stable").supportsItemModelAndTooltipStyle());
        assertTrue(ServerVersion.fromBukkitVersion("26.3.build.151-beta").supportsItemModelAndTooltipStyle());
    }

    private static void assertSupported(String bukkitVersion) {
        assertTrue(ServerVersion.fromBukkitVersion(bukkitVersion).isSupported(), bukkitVersion);
    }

    @Test
    void choosesExactlyOneMythicSpawnLevelStageAcrossTheApiChange() {
        assertTrue(MythicMobsVersion.usesLegacySpawnLevelEvent("5.7.2-587f2e53"));
        assertTrue(MythicMobsVersion.usesLegacySpawnLevelEvent("5.8.0"));
        assertTrue(MythicMobsVersion.usesLegacySpawnLevelEvent("5.8.2-SNAPSHOT"));
        assertFalse(MythicMobsVersion.usesLegacySpawnLevelEvent("5.9.0"));
        assertFalse(MythicMobsVersion.usesLegacySpawnLevelEvent("5.12.1-46bae256"));
        assertFalse(MythicMobsVersion.usesLegacySpawnLevelEvent("5.13.1-SNAPSHOT-18927791"));
        assertFalse(MythicMobsVersion.usesLegacySpawnLevelEvent("6.0.0"));
        assertFalse(MythicMobsVersion.usesLegacySpawnLevelEvent(null));
    }

    private static void assertUnsupported(String bukkitVersion) {
        assertFalse(ServerVersion.fromBukkitVersion(bukkitVersion).isSupported(), bukkitVersion);
    }
}
