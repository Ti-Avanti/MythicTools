package gg.fotia.mythictools.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.lang.LocaleService;
import gg.fotia.mythictools.storage.PendingRewardQueue;
import gg.fotia.mythictools.storage.QueueResult;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RewardServiceTest {

    @Test
    void queuesOnlineInventoryOverflowWhenDroppingAtFeetIsDisabled() {
        PendingRewardQueue pendingRewards = mock(PendingRewardQueue.class);
        when(pendingRewards.queue(any(), any())).thenReturn(
                CompletableFuture.completedFuture(QueueResult.STORED));
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>(Map.of(
                0, new ItemStack(Material.DIAMOND, 2))));
        PluginSettings settings = settings(false);
        RewardService service = service(pendingRewards, settings, Runnable::run);
        UUID playerId = UUID.randomUUID();

        service.deliver(
                playerId,
                "Player",
                player,
                List.of(new RewardGrant(itemReward(), 5)),
                null,
                Map.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemStack>> items = ArgumentCaptor.forClass(List.class);
        verify(pendingRewards).queue(eq(playerId), items.capture());
        assertEquals(1, items.getValue().size());
        assertEquals(2, items.getValue().get(0).getAmount());
        verify(pendingRewards, never()).retainForRetry(any(), any());
    }

    @Test
    void queueFailureReattemptsOnlineInventoryThenDropsRemainingAtFeet() {
        PendingRewardQueue pendingRewards = mock(PendingRewardQueue.class);
        when(pendingRewards.queue(any(), any())).thenReturn(
                CompletableFuture.completedFuture(QueueResult.JOURNAL_FAILED));
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location location = new Location(world, 1.0, 2.0, 3.0);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(inventory.addItem(any(ItemStack.class)))
                .thenReturn(new HashMap<>(Map.of(0, new ItemStack(Material.DIAMOND, 2))))
                .thenReturn(new HashMap<>(Map.of(0, new ItemStack(Material.DIAMOND, 1))));
        ManualExecutor fallback = new ManualExecutor();
        PluginSettings settings = settings(false);
        RewardService service = service(pendingRewards, settings, fallback);

        service.deliver(
                UUID.randomUUID(),
                "Player",
                player,
                List.of(new RewardGrant(itemReward(), 5)),
                null,
                Map.of());

        assertEquals(1, fallback.size());
        fallback.runNext();
        ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
        verify(world).dropItemNaturally(eq(location), dropped.capture());
        assertEquals(1, dropped.getValue().getAmount());
        verify(pendingRewards, never()).retainForRetry(any(), any());
    }

    @Test
    void exceptionalQueueFailureRetainsOfflineItemsForLaterClaim() {
        PendingRewardQueue pendingRewards = mock(PendingRewardQueue.class);
        when(pendingRewards.queue(any(), any())).thenReturn(
                CompletableFuture.failedFuture(new SQLException("database failed")));
        when(pendingRewards.retainForRetry(any(), any())).thenReturn(
                CompletableFuture.completedFuture(QueueResult.STORED));
        ManualExecutor fallback = new ManualExecutor();
        PluginSettings settings = settings(false);
        RewardService service = service(pendingRewards, settings, fallback);
        UUID playerId = UUID.randomUUID();

        service.deliver(
                playerId,
                "OfflinePlayer",
                null,
                List.of(new RewardGrant(itemReward(), 3)),
                null,
                Map.of());

        assertEquals(0, fallback.size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemStack>> retained = ArgumentCaptor.forClass(List.class);
        verify(pendingRewards).retainForRetry(eq(playerId), retained.capture());
        assertEquals(3, retained.getValue().get(0).getAmount());
    }

    @Test
    void logsUnrecoverableOfflineJournalFailure() {
        PendingRewardQueue pendingRewards = mock(PendingRewardQueue.class);
        when(pendingRewards.queue(any(), any())).thenReturn(
                CompletableFuture.completedFuture(QueueResult.JOURNAL_FAILED));
        when(pendingRewards.retainForRetry(any(), any())).thenReturn(
                CompletableFuture.completedFuture(QueueResult.JOURNAL_FAILED));
        RecordingHandler records = new RecordingHandler();
        Logger logger = Logger.getLogger("RewardServiceTest-journal-failure");
        logger.setUseParentHandlers(false);
        logger.addHandler(records);
        PluginSettings settings = settings(false);
        RewardService service = new RewardService(
                null,
                pendingRewards,
                new LocaleService(null, settings),
                null,
                settings,
                ignored -> null,
                Runnable::run,
                logger);

        service.deliver(
                UUID.randomUUID(),
                "OfflinePlayer",
                null,
                List.of(new RewardGrant(itemReward(), 1)),
                null,
                Map.of());

        assertTrue(records.records.stream().anyMatch(record ->
                record.getLevel().intValue() >= Level.SEVERE.intValue()
                        && record.getMessage().contains("无法持久保存")));
    }

    private static RewardService service(
            PendingRewardQueue pendingRewards,
            PluginSettings settings,
            Executor fallbackExecutor) {
        return new RewardService(
                null,
                pendingRewards,
                new LocaleService(null, settings),
                null,
                settings,
                ignored -> null,
                fallbackExecutor,
                Logger.getLogger("RewardServiceTest"));
    }

    private static RewardEntry itemReward() {
        return new RewardEntry(
                "diamond",
                RewardType.ITEM,
                1,
                1,
                64,
                "common",
                Map.of("zh_CN", "钻石"),
                Map.of("zh_CN", ""),
                new ItemStack(Material.DIAMOND),
                RewardDelivery.INVENTORY,
                "",
                CommandExecutorType.CONSOLE);
    }

    private static PluginSettings settings(boolean dropOverflowAtFeet) {
        return new PluginSettings(
                "zh_CN",
                false,
                Map.of(),
                true,
                true,
                true,
                100L,
                12,
                12,
                32,
                dropOverflowAtFeet,
                "<?>" ,
                "data.db",
                false);
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            tasks.remove().run();
        }

        private int size() {
            return tasks.size();
        }
    }

    private static final class RecordingHandler extends Handler {
        private final List<LogRecord> records = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
