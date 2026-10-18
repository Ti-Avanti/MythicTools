package gg.fotia.mythictools.gui.summoning;

import java.util.List;

/** 页面结构只描述已实现字段；显示文本全部来自语言文件。 */
final class SummonEditorFields {
    record Field(String path, Kind kind, double min, double max, double step, List<String> choices) {
        static Field text(String path) { return new Field(path, Kind.TEXT, 0, 0, 0, List.of()); }
        static Field number(String path, double min, double max, double step) { return new Field(path, Kind.NUMBER, min, max, step, List.of()); }
        static Field bool(String path) { return new Field(path, Kind.BOOLEAN, 0, 0, 0, List.of()); }
        static Field select(String path, String... values) { return new Field(path, Kind.SELECT, 0, 0, 0, List.of(values)); }
        static Field list(String path) { return new Field(path, Kind.LIST, 0, 0, 0, List.of()); }
    }
    enum Kind { TEXT, NUMBER, BOOLEAN, SELECT, LIST }
    static List<Field> group(String group) {
        return switch (group) {
            case "item" -> List.of(Field.text("item.material"), Field.text("item.display-name"), Field.list("item.lore"),
                    Field.number("item.custom-model-data", 0, Integer.MAX_VALUE, 1), Field.text("item.item-model"), Field.text("item.tooltip-style"));
            case "rules" -> List.of(Field.bool("enabled"), Field.text("boss-id"), Field.number("consume-amount", 0, 64, 1),
                    Field.number("cooldown-seconds", 0, 86400, 1), Field.text("permission"), Field.list("worlds"),
                    Field.select("spawn.mode", "player-location", "target-block"), Field.number("spawn.target-distance", 1, 32, 1),
                    Field.number("max-active", 1, 1000, 1));
            case "windup" -> List.of(Field.number("windup.seconds", 0.1, 300, 0.5), Field.number("windup.charge-start", 0, 1, 0.05), Field.number("windup.move-radius", 0, 64, 0.5),
                    Field.bool("windup.damage-interrupt"), Field.select("windup.countdown", "actionbar", "title", "chat", "none"));
            case "visual" -> List.of(Field.select("visual.type", "none", "modelengine", "bettermodel", "craftengine"),
                    Field.text("visual.id"), Field.bool("visual.during-windup"), Field.number("visual.duration-seconds", 0, 3600, 1),
                    Field.number("visual.scale", 0.05, 20, 0.1), Field.number("visual.offset.x", -16, 16, 0.1),
                    Field.number("visual.offset.y", -16, 16, 0.1), Field.number("visual.offset.z", -16, 16, 0.1),
                    Field.number("visual.yaw", -360, 360, 15), Field.bool("visual.remove-on-boss-end"),
                    Field.number("visual.rotation", -360, 360, 10), Field.number("visual.float-height", 0, 3, 0.1),
                    Field.text("visual.animations.start"), Field.text("visual.animations.charge"), Field.text("visual.animations.release"),
                    Field.text("visual.animations.idle"), Field.text("visual.animations.end"), Field.number("visual.end-seconds", 0, 10, 0.1),
                    Field.number("visual.release-seconds", 0, 30, 0.1));
            case "layer" -> List.of(Field.text("preset"), Field.bool("enabled"), Field.number("start", 0, 1, 0.05), Field.number("end", 0, 1, 0.05),
                    Field.number("radius", 0.1, 16, 0.1), Field.number("height", 0.1, 16, 0.1), Field.number("speed", -10, 10, 0.1),
                    Field.number("density", 8, 256, 8), Field.text("primary"), Field.text("secondary"),
                    Field.select("accent-particle", gg.fotia.mythictools.summoning.effect.EffectLayer.ACCENTS.toArray(String[]::new)),
                    Field.number("offset.x", -16, 16, 0.1), Field.number("offset.y", -16, 16, 0.1), Field.number("offset.z", -16, 16, 0.1),
                    Field.bool("release"), Field.number("release-seconds", 0.1, 10, 0.1));
            case "messages" -> List.of(Field.text("messages.start"), Field.text("messages.countdown"), Field.text("messages.success"), Field.text("messages.cancelled"));
            case "sound-start", "sound-charge", "sound-success", "sound-cancel" -> {
                String path = "sounds." + group.substring(6);
                yield List.of(Field.text(path + ".sound"), Field.number(path + ".volume", 0, 4, 0.1), Field.number(path + ".pitch", 0.5, 2, 0.1));
            }
            default -> List.of();
        };
    }
}
