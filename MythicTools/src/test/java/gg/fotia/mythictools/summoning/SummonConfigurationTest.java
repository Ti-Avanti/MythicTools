package gg.fotia.mythictools.summoning;

import gg.fotia.mythictools.summoning.config.SummonNumbers;
import gg.fotia.mythictools.summoning.config.SummonRepository;
import gg.fotia.mythictools.summoning.effect.EffectLayer;
import gg.fotia.mythictools.summoning.effect.EffectShape;
import gg.fotia.mythictools.summoning.visual.VisualConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SummonConfigurationTest {
    @Test void allEightBundledShapesHaveValidDistinctDefinitions() throws Exception {
        java.util.Set<EffectShape> shapes = java.util.EnumSet.noneOf(EffectShape.class);
        for (EffectShape shape : EffectShape.values()) {
            try (var stream = getClass().getResourceAsStream("/summoning/effects/" + shape.key() + ".yml")) {
                assertNotNull(stream);
                var yaml = SummonRepository.yaml(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                var effect = EffectLayer.read(shape.key(), yaml);
                assertEquals(shape, effect.shape());
                assertTrue(shapes.add(effect.shape()));
            }
        }
        assertEquals(8, shapes.size());
    }
    @Test void invalidTimelinesColorsAndNonFiniteNumbersCannotReachRenderer() {
        var yaml = SummonRepository.yaml("shape: arcane\nstart: 0.9\nend: 0.2\n");
        assertThrows(IllegalArgumentException.class, () -> EffectLayer.read("test", yaml));
        yaml.set("end", 1); yaml.set("radius", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> EffectLayer.read("test", yaml));
        yaml.set("radius", 2); yaml.set("primary", "purple");
        assertThrows(IllegalArgumentException.class, () -> EffectLayer.read("test", yaml));
        assertThrows(IllegalArgumentException.class, () -> SummonNumbers.id("../outside"));
    }
    @Test void visualLifetimeIsIndependentOfWindup() {
        var yaml = SummonRepository.yaml("windup:\n  seconds: 5\nvisual:\n  duration-seconds: 30\n");
        assertEquals(600, VisualConfig.read(yaml).durationTicks());
        yaml.set("windup.seconds", 20);
        assertEquals(600, VisualConfig.read(yaml).durationTicks());
    }
}
