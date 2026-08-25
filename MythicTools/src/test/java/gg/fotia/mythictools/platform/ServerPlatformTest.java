package gg.fotia.mythictools.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ServerPlatformTest {
    @Test
    void detectsSpigotWithoutTreatingVersionTextAsPaper() {
        assertEquals(ServerPlatform.SPIGOT,
                ServerPlatform.detect("CraftBukkit", "git-Spigot-2f5d615-aae46bb (MC: 1.20.1)"));
    }

    @Test
    void detectsPaperAndKnownPaperForks() {
        assertEquals(ServerPlatform.PAPER,
                ServerPlatform.detect("Paper", "1.21.11-69-main@abc123"));
        assertEquals(ServerPlatform.PAPER,
                ServerPlatform.detect("Leaf", "Leaf 1.21.11"));
        assertEquals(ServerPlatform.PAPER,
                ServerPlatform.detect("Purpur", "Purpur-1.21.4"));
    }

    @Test
    void unknownForkUsesSafeSpigotBridge() {
        assertEquals(ServerPlatform.SPIGOT,
                ServerPlatform.detect("UnknownFork", "custom server 1.21.4"));
    }
}
