package gg.fotia.mythictools.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** 在 Adventure 组件与 Spigot 可接受的 JSON/旧颜色文本之间转换。 */
final class SpigotComponentCodec {
    private static final GsonComponentSerializer JSON = GsonComponentSerializer.gson();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    String json(Component component) {
        return JSON.serialize(component);
    }

    String legacy(Component component) {
        return LEGACY.serialize(component);
    }

    Component fromLegacy(String input) {
        return LEGACY.deserialize(input == null ? "" : input);
    }
}
