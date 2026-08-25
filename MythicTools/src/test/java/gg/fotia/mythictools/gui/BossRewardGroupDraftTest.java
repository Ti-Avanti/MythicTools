package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BossRewardGroupDraftTest {
    private static final String PATH = "rewards.killer.groups";

    @Test
    void retryLocatesTheOriginalRewardAfterAnExternalInsertion() {
        YamlConfiguration original = groups(
                Map.of("group", "common", "copies", 1),
                Map.of("group", "rare", "copies", 2));
        BossRewardGroupDraft draft = BossRewardGroupDraft.load(original, PATH, 1);
        draft.copies(4);

        YamlConfiguration refreshed = groups(
                Map.of("group", "event", "copies", 1),
                Map.of("group", "common", "copies", 1),
                Map.of("group", "rare", "copies", 2));

        draft.applyTo(refreshed, PATH);

        assertEquals("common", refreshed.getMapList(PATH).get(1).get("group"));
        assertEquals(4, ((Number) refreshed.getMapList(PATH).get(2).get("copies")).intValue());
    }

    @Test
    void retryRefusesToOverwriteAnotherRewardWhenTheSourceWasRemoved() {
        YamlConfiguration original = groups(
                Map.of("group", "common", "copies", 1),
                Map.of("group", "rare", "copies", 2));
        BossRewardGroupDraft draft = BossRewardGroupDraft.load(original, PATH, 1);
        YamlConfiguration refreshed = groups(Map.of("group", "common", "copies", 1));

        assertThrows(IllegalStateException.class, () -> draft.applyTo(refreshed, PATH));
        assertEquals("common", refreshed.getMapList(PATH).get(0).get("group"));
    }

    @SafeVarargs
    private static YamlConfiguration groups(Map<String, Object>... values) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(PATH, List.of(values));
        return yaml;
    }
}
