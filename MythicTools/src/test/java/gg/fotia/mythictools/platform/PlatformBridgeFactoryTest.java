package gg.fotia.mythictools.platform;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class PlatformBridgeFactoryTest {
    private final Logger logger = Logger.getLogger("platform-test");

    @Test
    void selectsDedicatedBridgeForDetectedPlatform() {
        assertInstanceOf(PaperPlatformBridge.class,
                PlatformBridgeFactory.create(ServerPlatform.PAPER, logger));
        assertInstanceOf(SpigotPlatformBridge.class,
                PlatformBridgeFactory.create(ServerPlatform.SPIGOT, logger));
    }

    @Test
    void spigotBridgeUsesLegacyInventoryMetadata() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getLore()).thenReturn(List.of("§b原有"));
        PlatformBridge bridge = PlatformBridgeFactory.create(ServerPlatform.SPIGOT, logger);

        bridge.displayName(meta, Component.text("名称"));
        List<Component> lore = bridge.lore(meta);
        bridge.lore(meta, lore);

        verify(meta).setDisplayName("名称");
        verify(meta).setLore(List.of("§b原有"));
    }

    @Test
    void paperBridgeKeepsNativeAdventureInventoryMetadata() {
        ItemMeta meta = mock(ItemMeta.class);
        Component name = Component.text("名称");
        PlatformBridge bridge = PlatformBridgeFactory.create(ServerPlatform.PAPER, logger);

        bridge.displayName(meta, name);

        verify(meta).displayName(name);
    }
}
