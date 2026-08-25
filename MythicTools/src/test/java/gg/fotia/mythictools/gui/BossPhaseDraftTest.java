package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BossPhaseDraftTest {

    @Test
    void editsStayIsolatedUntilTheDraftIsApplied() {
        YamlConfiguration yaml = phases(
                Map.of("mob", "FirstBoss", "level", 1.0),
                Map.of("mob", "SecondBoss", "level", 2.0));

        BossPhaseDraft draft = BossPhaseDraft.load(yaml, 1);
        draft.mobId("FinalBoss");
        draft.level(3.5);

        assertEquals("SecondBoss", yaml.getMapList("phases").get(1).get("mob"));
        draft.applyTo(yaml, 1);
        assertEquals("FinalBoss", yaml.getMapList("phases").get(1).get("mob"));
        assertEquals(3.5, ((Number) yaml.getMapList("phases").get(1).get("level")).doubleValue());
    }

    @Test
    void appendsAndMovesPhasesWithoutLosingTheirOrder() {
        YamlConfiguration yaml = phases(
                Map.of("mob", "FirstBoss", "level", 1.0),
                Map.of("mob", "SecondBoss", "level", 2.0));

        BossPhaseDraft.create("ThirdBoss").applyTo(yaml, 2);
        int movedIndex = BossPhaseDraft.move(yaml, 2, -1);

        assertEquals(1, movedIndex);
        assertEquals(List.of("FirstBoss", "ThirdBoss", "SecondBoss"), mobIds(yaml));
    }

    @Test
    void moveAtASequenceBoundaryDoesNothing() {
        YamlConfiguration yaml = phases(Map.of("mob", "OnlyBoss", "level", 1.0));

        assertEquals(0, BossPhaseDraft.move(yaml, 0, -1));
        assertEquals(List.of("OnlyBoss"), mobIds(yaml));
    }

    @Test
    void refusesToDeleteTheLastPhase() {
        YamlConfiguration yaml = phases(Map.of("mob", "OnlyBoss", "level", 1.0));

        assertThrows(IllegalStateException.class, () -> BossPhaseDraft.remove(yaml, 0));
        assertEquals(1, BossPhaseDraft.count(yaml));
    }

    @Test
    void acceptsOnlyFinitePositiveLevels() {
        BossPhaseDraft draft = BossPhaseDraft.create("Boss");

        draft.level(0.5);
        assertEquals(0.5, draft.level());
        assertThrows(IllegalArgumentException.class, () -> draft.level(0.0));
        assertThrows(IllegalArgumentException.class, () -> draft.level(-1.0));
        assertThrows(IllegalArgumentException.class, () -> draft.level(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> draft.level(Double.POSITIVE_INFINITY));
    }

    @Test
    void retryLocatesTheOriginalPhaseAfterAnExternalInsertion() {
        YamlConfiguration original = phases(
                Map.of("mob", "FirstBoss", "level", 1.0),
                Map.of("mob", "SecondBoss", "level", 2.0));
        BossPhaseDraft draft = BossPhaseDraft.load(original, 1);
        draft.mobId("FinalBoss");

        YamlConfiguration refreshed = phases(
                Map.of("mob", "EventBoss", "level", 9.0),
                Map.of("mob", "FirstBoss", "level", 1.0),
                Map.of("mob", "SecondBoss", "level", 2.0));

        draft.applyTo(refreshed, 1);

        assertEquals(List.of("EventBoss", "FirstBoss", "FinalBoss"), mobIds(refreshed));
    }

    @Test
    void retryRefusesToOverwriteAnotherPhaseWhenTheSourceWasRemoved() {
        YamlConfiguration original = phases(
                Map.of("mob", "FirstBoss", "level", 1.0),
                Map.of("mob", "SecondBoss", "level", 2.0));
        BossPhaseDraft draft = BossPhaseDraft.load(original, 1);
        YamlConfiguration refreshed = phases(Map.of("mob", "FirstBoss", "level", 1.0));

        assertThrows(IllegalStateException.class, () -> draft.applyTo(refreshed, 1));
        assertEquals(List.of("FirstBoss"), mobIds(refreshed));
    }

    @SafeVarargs
    private static YamlConfiguration phases(Map<String, Object>... values) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("phases", List.of(values));
        return yaml;
    }

    private static List<String> mobIds(YamlConfiguration yaml) {
        return yaml.getMapList("phases").stream()
                .map(phase -> String.valueOf(phase.get("mob")))
                .toList();
    }
}
