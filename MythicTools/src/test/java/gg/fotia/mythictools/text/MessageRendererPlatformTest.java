package gg.fotia.mythictools.text;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.lang.LocaleService;
import gg.fotia.mythictools.platform.PlatformBridge;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class MessageRendererPlatformTest {
    @Test
    void delegatesRenderedPlayerMessagesToPlatformBridge() {
        LocaleService locales = inlineMock(LocaleService.class);
        PluginSettings settings = inlineMock(PluginSettings.class);
        PluginManager plugins = mock(PluginManager.class);
        PlatformBridge platform = mock(PlatformBridge.class);
        Player player = mock(Player.class);
        when(locales.text(player, "common.saved")).thenReturn("<!i><green>已保存");
        when(settings.missingImageFallback()).thenReturn("<?>");

        MessageRenderer renderer = new MessageRenderer(
                locales, settings, plugins, Logger.getLogger("renderer-test"), platform);

        renderer.send(player, "common.saved", Map.of());

        verify(platform).send(eq(player), any(Component.class));
    }

    private static <T> T inlineMock(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.INLINE));
    }
}
