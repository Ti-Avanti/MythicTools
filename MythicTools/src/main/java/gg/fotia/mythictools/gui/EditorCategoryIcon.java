package gg.fotia.mythictools.gui;

import org.bukkit.Material;

/** Maps editor category keys to recognizable vanilla icons. */
final class EditorCategoryIcon {
    private EditorCategoryIcon() {
    }

    static Material forKey(String key) {
        if (key == null) {
            return Material.BOOK;
        }
        return switch (key) {
            case "biome-basic" -> Material.OAK_SAPLING;
            case "biome-trigger" -> Material.REDSTONE_TORCH;
            case "biome-location" -> Material.COMPASS;
            case "biome-limits" -> Material.IRON_BARS;
            case "mob-drop-basic" -> Material.ROTTEN_FLESH;
            case "mob-drop-groups" -> Material.BARREL;
            case "drop-item-rewards" -> Material.DIAMOND;
            case "drop-command-rewards" -> Material.COMMAND_BLOCK;
            case "mob-group-amount" -> Material.DROPPER;
            case "mob-group-members" -> Material.SPAWNER;
            case "boss-basic" -> Material.NETHER_STAR;
            case "boss-loot" -> Material.CHEST;
            case "boss-biome-spawning" -> Material.GRASS_BLOCK;
            case "boss-point-spawning" -> Material.LODESTONE;
            case "boss-point-schedule" -> Material.CLOCK;
            case "boss-broadcasts" -> Material.BELL;
            case "boss-ranking-rewards" -> Material.GOLD_INGOT;
            case "boss-killer-rewards" -> Material.DIAMOND_SWORD;
            default -> Material.BOOK;
        };
    }
}
