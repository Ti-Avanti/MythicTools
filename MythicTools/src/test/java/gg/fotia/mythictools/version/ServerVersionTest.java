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
    }

    @Test
    void rejectsVersionsOutsideTheDeclaredCompatibilityRange() {
        assertUnsupported("1.20.6-R0.1-SNAPSHOT");
        assertUnsupported("1.19.4-R0.1-SNAPSHOT");
        assertUnsupported("26.1.1-R0.1-SNAPSHOT");
        assertUnsupported("26.3-R0.1-SNAPSHOT");
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
    }

    private static void assertSupported(String bukkitVersion) {
        assertTrue(ServerVersion.fromBukkitVersion(bukkitVersion).isSupported(), bukkitVersion);
    }

    private static void assertUnsupported(String bukkitVersion) {
        assertFalse(ServerVersion.fromBukkitVersion(bukkitVersion).isSupported(), bukkitVersion);
    }
}
