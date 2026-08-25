package gg.fotia.mythictools.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gg.fotia.mythictools.config.RepositoryLoadContext;
import gg.fotia.mythictools.config.RepositorySnapshotStore;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeightedRewardSelectorTest {

    @TempDir
    Path dataFolder;

    @Test
    void selectsAcrossTotalsLargerThanIntegerMaximum() {
        RewardEntry first = entry("first", 3_000_000_000L);
        RewardEntry second = entry("second", 4_000_000_000L);
        List<RewardEntry> entries = List.of(first, second);

        assertEquals("first", WeightedRewardSelector.weightedAt(entries, RewardEntry::weight, 0L).id());
        assertEquals("first",
                WeightedRewardSelector.weightedAt(entries, RewardEntry::weight, 2_999_999_999L).id());
        assertEquals("second",
                WeightedRewardSelector.weightedAt(entries, RewardEntry::weight, 3_000_000_000L).id());
        assertEquals("second",
                WeightedRewardSelector.weightedAt(entries, RewardEntry::weight, 6_999_999_999L).id());
    }

    @Test
    void rejectsOutOfRangeTicketAndLongWeightOverflow() {
        List<RewardEntry> entries = List.of(entry("only", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> WeightedRewardSelector.weightedAt(entries, RewardEntry::weight, -1L));
        assertThrows(IllegalArgumentException.class,
                () -> WeightedRewardSelector.weightedAt(entries, RewardEntry::weight, 1L));

        List<RewardEntry> overflow = List.of(
                entry("first", Long.MAX_VALUE), entry("second", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> WeightedRewardSelector.weightedAt(overflow, RewardEntry::weight, 0L));
    }

    @Test
    void selectsLegalItemAmountWhoseMaximumIsIntegerMaximum() {
        RewardEntry huge = new RewardEntry(
                "huge", RewardType.ITEM, 1L, 1, Integer.MAX_VALUE, "common",
                Map.of("zh_CN", "huge"), Map.of(), null, RewardDelivery.GROUND,
                null, CommandExecutorType.CONSOLE);
        Map<String, DropGroup> groups = new LinkedHashMap<>();
        groups.put("huge", new DropGroup("huge", List.of(huge)));
        RepositorySnapshotStore store = new RepositorySnapshotStore();
        store.publish(store.current().withRewards(
                new RewardSnapshot(Map.of(), groups, Map.of())));
        RewardRepository repository = new RewardRepository(
                new RepositoryLoadContext(
                        dataFolder.toFile(), Logger.getLogger("WeightedRewardSelectorTest"),
                        ignored -> true, ignored -> null),
                store);
        WeightedRewardSelector selector = new WeightedRewardSelector(repository);

        RewardGrant grant = assertDoesNotThrow(() -> selector.selectGroup("huge", 1).get(0));

        org.junit.jupiter.api.Assertions.assertTrue(grant.amount() >= 1);
        org.junit.jupiter.api.Assertions.assertTrue(grant.amount() <= Integer.MAX_VALUE);
    }

    private static RewardEntry entry(String id, long weight) {
        return new RewardEntry(
                id, RewardType.COMMAND, weight, 1, 1, "common",
                Map.of("zh_CN", id), Map.of(), null, null,
                "say test", CommandExecutorType.CONSOLE);
    }
}
