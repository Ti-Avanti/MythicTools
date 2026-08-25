package gg.fotia.mythictools.lang;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.fotia.mythictools.storage.ClaimResult;
import gg.fotia.mythictools.storage.PendingRewardDelivery;
import gg.fotia.mythictools.storage.PendingRewardQueue;
import gg.fotia.mythictools.storage.QueueResult;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class PlayerLocaleListenerTest {

    @Test
    void returnsEveryItemWhenPlayerIsOffline() {
        List<ItemStack> items = List.of(new ItemStack(Material.DIAMOND, 4));

        PlayerLocaleListener.DeliveryResult result = PlayerLocaleListener.calculateDelivery(
                false, items, List.of(), false);

        assertEquals(0, result.deliveredAmount());
        assertEquals(1, result.leftovers().size());
        assertEquals(4, result.leftovers().get(0).getAmount());
    }

    @Test
    void countsOnlyItemsAddedWhenOverflowIsNotDropped() {
        List<ItemStack> items = List.of(new ItemStack(Material.IRON_INGOT, 5));
        List<ItemStack> overflow = List.of(new ItemStack(Material.IRON_INGOT, 2));

        PlayerLocaleListener.DeliveryResult result = PlayerLocaleListener.calculateDelivery(
                true, items, overflow, false);

        assertEquals(3, result.deliveredAmount());
        assertEquals(2, result.leftovers().get(0).getAmount());
    }

    @Test
    void countsAddedAndDroppedItemsWhenOverflowIsDropped() {
        List<ItemStack> items = List.of(new ItemStack(Material.GOLD_INGOT, 5));
        List<ItemStack> overflow = List.of(new ItemStack(Material.GOLD_INGOT, 2));

        PlayerLocaleListener.DeliveryResult result = PlayerLocaleListener.calculateDelivery(
                true, items, overflow, true);

        assertEquals(5, result.deliveredAmount());
        assertTrue(result.leftovers().isEmpty());
    }

    @Test
    void messageFailureCannotFailDeliverySettlement() {
        ImmediateQueue queue = new ImmediateQueue(List.of(new ItemStack(Material.STONE, 2)));
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        PlayerLocaleListener listener = new PlayerLocaleListener(
                null,
                queue,
                false,
                Runnable::run,
                (ignored, amount) -> {
                    throw new IllegalStateException("message failed");
                },
                quietLogger());

        assertDoesNotThrow(() -> listener.handleJoin(player));

        assertTrue(queue.deliveryReturned);
        assertTrue(queue.leftovers.isEmpty());
    }

    @Test
    void busyJoinRetriesOnceAfterCurrentClaimCompletes() {
        BusyThenAcceptedQueue queue = new BusyThenAcceptedQueue();
        ManualExecutor mainThread = new ManualExecutor();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        PlayerLocaleListener listener = new PlayerLocaleListener(
                null,
                queue,
                false,
                mainThread,
                (ignored, amount) -> { },
                quietLogger());

        listener.handleJoin(player);
        assertEquals(1, queue.claims);
        assertEquals(0, mainThread.size());

        queue.activeClaim.complete(null);
        assertEquals(1, mainThread.size());
        mainThread.runNext();

        assertEquals(2, queue.claims);
        assertEquals(0, mainThread.size());
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger("PlayerLocaleListenerTest");
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static final class ImmediateQueue implements PendingRewardQueue {
        private final List<ItemStack> items;
        private boolean deliveryReturned;
        private List<ItemStack> leftovers = List.of();

        private ImmediateQueue(List<ItemStack> items) {
            this.items = items;
        }

        @Override
        public CompletionStage<QueueResult> queue(UUID playerId, List<ItemStack> queuedItems) {
            return CompletableFuture.completedFuture(QueueResult.STORED);
        }

        @Override
        public CompletionStage<QueueResult> retainForRetry(UUID playerId, List<ItemStack> retainedItems) {
            return CompletableFuture.completedFuture(QueueResult.STORED);
        }

        @Override
        public ClaimResult claim(UUID playerId, PendingRewardDelivery delivery) {
            leftovers = delivery.deliver(items);
            deliveryReturned = true;
            return new ClaimResult(
                    ClaimResult.Status.ACCEPTED,
                    CompletableFuture.completedFuture(null));
        }
    }

    private static final class BusyThenAcceptedQueue implements PendingRewardQueue {
        private final CompletableFuture<Void> activeClaim = new CompletableFuture<>();
        private int claims;

        @Override
        public CompletionStage<QueueResult> queue(UUID playerId, List<ItemStack> items) {
            return CompletableFuture.completedFuture(QueueResult.STORED);
        }

        @Override
        public CompletionStage<QueueResult> retainForRetry(UUID playerId, List<ItemStack> items) {
            return CompletableFuture.completedFuture(QueueResult.STORED);
        }

        @Override
        public ClaimResult claim(UUID playerId, PendingRewardDelivery delivery) {
            claims++;
            if (claims == 1) {
                return new ClaimResult(ClaimResult.Status.BUSY, activeClaim);
            }
            return new ClaimResult(
                    ClaimResult.Status.ACCEPTED,
                    CompletableFuture.completedFuture(null));
        }
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
}
