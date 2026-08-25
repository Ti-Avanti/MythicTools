package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gg.fotia.mythictools.config.SafetyLimits;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MobDropGroupDraftTest {

    private static final SafetyLimits LIMITS = new SafetyLimits(
            16, 2304, 16, 100_000, 1_000_000L, 16, 32, 20);

    @Test
    void editsOneMapListEntryWithoutMutatingTheOriginalDraftSource() {
        YamlConfiguration yaml = yamlWithGroups(
                Map.of("id", "common", "weight", 30, "min-amount", 0, "max-amount", 2),
                Map.of("id", "rare", "weight", 5, "min-amount", 1, "max-amount", 1));

        MobDropGroupDraft draft = MobDropGroupDraft.load(yaml, 1);
        draft.weight(40);
        draft.minimum(0);
        draft.maximum(3);

        assertEquals(5L, ((Number) yaml.getMapList("groups").get(1).get("weight")).longValue());

        draft.applyTo(yaml);

        assertEquals("common", yaml.getMapList("groups").get(0).get("id"));
        assertEquals("rare", yaml.getMapList("groups").get(1).get("id"));
        assertEquals(40L, ((Number) yaml.getMapList("groups").get(1).get("weight")).longValue());
        assertEquals(0, ((Number) yaml.getMapList("groups").get(1).get("min-amount")).intValue());
        assertEquals(3, ((Number) yaml.getMapList("groups").get(1).get("max-amount")).intValue());
    }

    @Test
    void appendsSelectedGroupAndDeletesOnlyTheRequestedEntry() {
        YamlConfiguration yaml = yamlWithGroups(
                Map.of("id", "common", "weight", 30, "min-amount", 0, "max-amount", 2));

        MobDropGroupDraft.create("rare").applyTo(yaml);
        assertEquals(List.of("common", "rare"), MobDropGroupDraft.groupIds(yaml));

        MobDropGroupDraft.remove(yaml, 0);
        assertEquals(List.of("rare"), MobDropGroupDraft.groupIds(yaml));
    }

    @Test
    void allowsDeletingTheLastAssignmentBecauseRuntimeRulesAllowAnEmptyList() {
        YamlConfiguration yaml = yamlWithGroups(
                Map.of("id", "common", "weight", 30, "min-amount", 0, "max-amount", 2));

        MobDropGroupDraft.remove(yaml, 0);

        assertEquals(List.of(), yaml.getMapList("groups"));
    }

    @Test
    void enforcesConfiguredWeightAndPerMobQuantityLimits() {
        MobDropGroupDraft draft = MobDropGroupDraft.create("common");

        draft.weight(LIMITS.maxWeight());
        draft.minimum(0);
        draft.maximum(LIMITS.maxDropsPerMob());
        draft.validate(LIMITS);

        draft.weight(LIMITS.maxWeight() + 1L);
        assertThrows(IllegalArgumentException.class, () -> draft.validate(LIMITS));

        draft.weight(1L);
        draft.minimum(4);
        draft.maximum(3);
        assertThrows(IllegalArgumentException.class, () -> draft.validate(LIMITS));

        draft.minimum(0);
        draft.maximum(LIMITS.maxDropsPerMob() + 1);
        assertThrows(IllegalArgumentException.class, () -> draft.validate(LIMITS));
    }

    @Test
    void retryLocatesTheOriginalAssignmentAfterAnExternalInsertion() {
        YamlConfiguration original = yamlWithGroups(
                Map.of("id", "common", "weight", 30, "min-amount", 0, "max-amount", 2),
                Map.of("id", "rare", "weight", 5, "min-amount", 1, "max-amount", 1));
        MobDropGroupDraft draft = MobDropGroupDraft.load(original, 1);
        draft.weight(40);

        YamlConfiguration refreshed = yamlWithGroups(
                Map.of("id", "event", "weight", 1, "min-amount", 1, "max-amount", 1),
                Map.of("id", "common", "weight", 30, "min-amount", 0, "max-amount", 2),
                Map.of("id", "rare", "weight", 5, "min-amount", 1, "max-amount", 1));

        draft.applyTo(refreshed);

        assertEquals(List.of("event", "common", "rare"), MobDropGroupDraft.groupIds(refreshed));
        assertEquals(30L, ((Number) refreshed.getMapList("groups").get(1).get("weight")).longValue());
        assertEquals(40L, ((Number) refreshed.getMapList("groups").get(2).get("weight")).longValue());
    }

    @Test
    void retryRefusesToOverwriteAnotherAssignmentWhenTheSourceWasRemoved() {
        YamlConfiguration original = yamlWithGroups(
                Map.of("id", "common", "weight", 30, "min-amount", 0, "max-amount", 2),
                Map.of("id", "rare", "weight", 5, "min-amount", 1, "max-amount", 1));
        MobDropGroupDraft draft = MobDropGroupDraft.load(original, 1);
        YamlConfiguration refreshed = yamlWithGroups(
                Map.of("id", "common", "weight", 30, "min-amount", 0, "max-amount", 2));

        assertThrows(IllegalStateException.class, () -> draft.applyTo(refreshed));
        assertEquals(List.of("common"), MobDropGroupDraft.groupIds(refreshed));
    }

    @SafeVarargs
    private static YamlConfiguration yamlWithGroups(Map<String, Object>... groups) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("groups", List.of(groups));
        return yaml;
    }
}
