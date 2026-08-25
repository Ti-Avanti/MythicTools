package gg.fotia.mythictools.reward;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

/** 可配置稀有度。 */
public record Rarity(String id, Map<String, String> displays, String color, int priority) {
    /** 获取指定语言的显示文本。 */
    public String display(String locale) {
        return displays.getOrDefault(locale, displays.getOrDefault("zh_CN", id));
    }

    /** 返回稀有度选择菜单使用的颜色图标。 */
    public Material selectorMaterial() {
        return switch (color == null ? "" : color.trim().toUpperCase(Locale.ROOT)) {
            case "BLACK" -> Material.BLACK_STAINED_GLASS_PANE;
            case "DARK_BLUE", "BLUE" -> Material.BLUE_STAINED_GLASS_PANE;
            case "DARK_GREEN", "GREEN" -> Material.GREEN_STAINED_GLASS_PANE;
            case "DARK_AQUA", "CYAN" -> Material.CYAN_STAINED_GLASS_PANE;
            case "DARK_RED", "RED" -> Material.RED_STAINED_GLASS_PANE;
            case "DARK_PURPLE", "PURPLE" -> Material.PURPLE_STAINED_GLASS_PANE;
            case "GOLD", "ORANGE" -> Material.ORANGE_STAINED_GLASS_PANE;
            case "GRAY" -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case "DARK_GRAY" -> Material.GRAY_STAINED_GLASS_PANE;
            case "AQUA", "LIGHT_BLUE" -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case "LIGHT_GREEN", "LIME" -> Material.LIME_STAINED_GLASS_PANE;
            case "LIGHT_PURPLE", "MAGENTA" -> Material.MAGENTA_STAINED_GLASS_PANE;
            case "YELLOW" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "WHITE" -> Material.WHITE_STAINED_GLASS_PANE;
            default -> Material.AMETHYST_SHARD;
        };
    }

    /** 按预设优先级与稳定 ID 顺序排列稀有度。 */
    public static List<Rarity> ordered(Collection<Rarity> values) {
        return values.stream()
                .sorted(Comparator.comparingInt(Rarity::priority)
                        .thenComparing(Rarity::id, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
