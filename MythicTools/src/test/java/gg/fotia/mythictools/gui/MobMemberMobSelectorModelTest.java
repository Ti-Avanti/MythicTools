package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MobMemberMobSelectorModelTest {

    @Test
    void paginatesLoadedMobsAndHighlightsTheCurrentMob() {
        var page = MobMemberMobSelectorModel.page(
                List.of("Zombie", "Alpha", "Skeleton", "Zombie", "Wither"),
                "Wither",
                1,
                2);

        assertEquals(1, page.number());
        assertEquals(List.of("Wither", "Zombie"),
                page.options().stream().map(MobMemberMobSelectorModel.Option::id).toList());
        assertTrue(page.options().get(0).selected());
        assertFalse(page.options().get(1).selected());
    }

    @Test
    void clampsPagesAndDoesNotAddAnUnloadedCurrentMob() {
        var first = MobMemberMobSelectorModel.page(List.of("Zombie", "Skeleton"), "Missing", -4, 1);
        var last = MobMemberMobSelectorModel.page(List.of("Zombie", "Skeleton"), "Missing", 99, 1);

        assertEquals(0, first.number());
        assertEquals(List.of("Skeleton"),
                first.options().stream().map(MobMemberMobSelectorModel.Option::id).toList());
        assertEquals(1, last.number());
        assertEquals(List.of("Zombie"),
                last.options().stream().map(MobMemberMobSelectorModel.Option::id).toList());
        assertTrue(first.options().stream().noneMatch(MobMemberMobSelectorModel.Option::selected));
        assertTrue(last.options().stream().noneMatch(MobMemberMobSelectorModel.Option::selected));
    }

    @Test
    void requiresAtLeastOneContentSlot() {
        assertThrows(IllegalArgumentException.class,
                () -> MobMemberMobSelectorModel.page(List.of("Zombie"), "Zombie", 0, 0));
    }
}
