package gg.fotia.mythictools.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class SpigotComponentCodecTest {
    private final SpigotComponentCodec codec = new SpigotComponentCodec();

    @Test
    void legacyTextPreservesRgbColorForInventoryMetadata() {
        String legacy = codec.legacy(Component.text("测试", TextColor.color(0x12AB34)));

        assertTrue(legacy.startsWith("§x§1§2§a§b§3§4"));
        assertTrue(legacy.endsWith("测试"));
    }

    @Test
    void jsonPreservesHoverEventsForSpigotChatComponents() {
        Component component = Component.text("排名")
                .hoverEvent(HoverEvent.showText(Component.text("奖励")));

        String json = codec.json(component);

        assertTrue(json.contains("hoverEvent"));
        assertTrue(json.contains("奖励"));
    }

    @Test
    void legacyRoundTripRetainsVisibleText() {
        Component restored = codec.fromLegacy("§a绿色");

        assertEquals("§a绿色", codec.legacy(restored));
    }
}
