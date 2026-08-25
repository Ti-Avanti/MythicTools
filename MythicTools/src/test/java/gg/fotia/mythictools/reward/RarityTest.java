package gg.fotia.mythictools.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RarityTest {

    @Test
    void mapsConfiguredColorToASelectorIcon() {
        Rarity rarity = new Rarity("rare", Map.of("zh_CN", "稀有"), "AQUA", 20);

        assertEquals(Material.LIGHT_BLUE_STAINED_GLASS_PANE, rarity.selectorMaterial());
    }

    @Test
    void usesAMethystIconForAnUnknownConfiguredColor() {
        Rarity rarity = new Rarity("custom", Map.<String, String>of(), "NOT_A_COLOR", 50);

        assertEquals(Material.AMETHYST_SHARD, rarity.selectorMaterial());
    }

    @Test
    void ordersRaritiesByPriorityThenId() {
        Rarity rare = new Rarity("rare", Map.<String, String>of(), "AQUA", 20);
        Rarity alpha = new Rarity("alpha", Map.<String, String>of(), "WHITE", 20);
        Rarity common = new Rarity("common", Map.<String, String>of(), "WHITE", 10);

        assertEquals(List.of(common, alpha, rare), Rarity.ordered(List.of(rare, common, alpha)));
    }
}
