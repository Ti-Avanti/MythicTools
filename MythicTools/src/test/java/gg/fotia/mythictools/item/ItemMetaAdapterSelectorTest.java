package gg.fotia.mythictools.item;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import gg.fotia.mythictools.version.ServerVersion;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ItemMetaAdapterSelectorTest {
    private static final Logger LOGGER = Logger.getLogger(ItemMetaAdapterSelectorTest.class.getName());

    @Test
    void selectsLegacyAdapterForPaperVersionsBefore1212() {
        ItemMetaAdapter adapter = ItemMetaAdapterSelector.select(
                ServerVersion.fromBukkitVersion("1.21.1-R0.1-SNAPSHOT"), LOGGER);

        assertInstanceOf(LegacyItemMetaAdapter.class, adapter);
    }

    @Test
    void selectsModernAdapterForPaperVersionsFrom1212Onward() {
        ItemMetaAdapter adapter = ItemMetaAdapterSelector.select(
                ServerVersion.fromBukkitVersion("1.21.2-R0.1-SNAPSHOT"), LOGGER);

        assertInstanceOf(ModernItemMetaAdapter.class, adapter);
    }
}
