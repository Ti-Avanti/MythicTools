package gg.fotia.mythictools.blackboxtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FixtureCatalogTest {
    @Test
    void aliasesNewAcceptanceNamesWithoutRemovingExistingModes() {
        FixtureCatalog catalog = new FixtureCatalog(List.of("spawn-point", "boss-manual", "drops-ground"));

        assertEquals("spawn-point", catalog.canonical("QA-POINT"));
        assertEquals("boss-manual", catalog.canonical("qa-boss"));
        assertEquals("drops-ground", catalog.canonical("drops-ground"));
        assertTrue(catalog.modes().containsAll(List.of(
                "qa-point", "qa-boss", "invalid-reload", "valid-reload", "limit-overflow",
                "reward-offline", "reward-overflow", "corrupt-pending", "chat-await", "external-remove")));
    }
}
