package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class RewardOptionSelectorTypeTest {

    @Test
    void exposesOnlySupportedDeliveryChoices() {
        RewardOptionSelectorType selector = RewardOptionSelectorType.fromField("delivery");

        assertEquals("delivery", selector.field());
        assertEquals(List.of("ground", "inventory"), selector.options().stream()
                .map(RewardOptionSelectorType.Option::value).toList());
        assertEquals(List.of('g', 'i'), selector.options().stream()
                .map(RewardOptionSelectorType.Option::symbol).toList());
    }

    @Test
    void exposesOnlySupportedExecutorChoices() {
        RewardOptionSelectorType selector = RewardOptionSelectorType.fromField("executor");

        assertEquals("executor", selector.field());
        assertEquals(List.of("console", "player"), selector.options().stream()
                .map(RewardOptionSelectorType.Option::value).toList());
        assertEquals(List.of('c', 'p'), selector.options().stream()
                .map(RewardOptionSelectorType.Option::symbol).toList());
    }

    @Test
    void ignoresFreeTextRewardFields() {
        assertNull(RewardOptionSelectorType.fromField("command"));
        assertNull(RewardOptionSelectorType.fromField("display.zh_CN"));
    }
}
