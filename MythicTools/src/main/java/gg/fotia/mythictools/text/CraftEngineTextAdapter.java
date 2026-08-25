package gg.fotia.mythictools.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.momirealms.craftengine.core.plugin.text.minimessage.ImageTag;
import net.momirealms.craftengine.core.util.AdventureHelper;

/** 将 CraftEngine 阴影 Adventure 组件桥接为 Paper Adventure 组件。 */
public final class CraftEngineTextAdapter {
    /** 使用 CraftEngine 自定义 MiniMessage 标签解析文本。 */
    public Component render(String input) {
        var shaded = AdventureHelper.miniMessage().deserialize(input, ImageTag.INSTANCE);
        String json = AdventureHelper.componentToJson(shaded);
        return GsonComponentSerializer.gson().deserialize(json);
    }
}
