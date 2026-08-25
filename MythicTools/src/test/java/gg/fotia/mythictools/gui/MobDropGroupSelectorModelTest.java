package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MobDropGroupSelectorModelTest {

    @Test
    void listsOnlyLoadedAndUnassignedDropGroupsInStablePages() {
        var page = MobDropGroupSelectorModel.page(
                List.of("rare", "common", "rare", "boss"),
                List.of("common"),
                0,
                2);

        assertEquals(0, page.number());
        assertEquals(List.of("boss", "rare"), page.options());
    }

    @Test
    void clampsPageAndRequiresAContentSlot() {
        var page = MobDropGroupSelectorModel.page(
                List.of("rare", "common", "boss"), List.of(), 99, 2);

        assertEquals(1, page.number());
        assertEquals(List.of("rare"), page.options());
        assertThrows(IllegalArgumentException.class,
                () -> MobDropGroupSelectorModel.page(List.of("rare"), List.of(), 0, 0));
    }
}
