package gg.fotia.mythictools.gui;

import java.util.Locale;
import org.bukkit.Material;

/** 将常见配置字段映射为可快速识别的原版图标。 */
final class EditorFieldIcon {
    private EditorFieldIcon() {
    }

    static Material forField(String field, Object value) {
        String normalized = field == null ? "" : field.toLowerCase(Locale.ROOT);
        String leaf = normalized.substring(normalized.lastIndexOf('.') + 1);
        if (normalized.contains("display.")) {
            return Material.NAME_TAG;
        }
        if (normalized.contains("message.")) {
            return Material.WRITABLE_BOOK;
        }
        return switch (leaf) {
            case "rarity" -> Material.AMETHYST_SHARD;
            case "weight" -> Material.GOLD_NUGGET;
            case "grant-mode" -> Material.NETHER_STAR;
            case "min", "min-amount", "min-distance", "minimum-online-players" -> Material.LIME_DYE;
            case "max", "max-amount", "max-distance", "max-active", "max-alive" -> Material.RED_DYE;
            case "command", "commands" -> Material.COMMAND_BLOCK;
            case "executor" -> Material.REPEATER;
            case "delivery" -> Material.CHEST_MINECART;
            case "enabled" -> Boolean.TRUE.equals(value) ? Material.LIME_DYE : Material.GRAY_DYE;
            case "mob", "mob-id" -> Material.ZOMBIE_HEAD;
            case "mob-group" -> Material.BUNDLE;
            case "world", "worlds" -> Material.GRASS_BLOCK;
            case "biomes" -> Material.OAK_SAPLING;
            case "chance" -> Material.ENDER_EYE;
            case "interval-seconds", "fallback-interval-seconds" -> Material.CLOCK;
            case "location", "x", "y", "z", "yaw", "pitch" -> Material.COMPASS;
            case "level" -> Material.EXPERIENCE_BOTTLE;
            case "phases" -> Material.WITHER_SKELETON_SKULL;
            case "limits", "nearby", "global", "radius" -> Material.IRON_BARS;
            case "amount" -> Material.BUNDLE;
            default -> Material.PAPER;
        };
    }
}
