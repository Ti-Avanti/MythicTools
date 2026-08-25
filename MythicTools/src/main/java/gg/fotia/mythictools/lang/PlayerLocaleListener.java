package gg.fotia.mythictools.lang;

import gg.fotia.mythictools.storage.ClaimResult;
import gg.fotia.mythictools.storage.PendingRewardQueue;
import gg.fotia.mythictools.text.MessageRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/** 处理语言缓存清理与离线奖励领取。 */
public final class PlayerLocaleListener implements Listener {
    private final LocaleService localeService;
    private final PendingRewardQueue pendingRewards;
    private final boolean dropOverflow;
    private final Executor mainThreadExecutor;
    private final BiConsumer<Player, Integer> deliveredMessage;
    private final Logger logger;

    public PlayerLocaleListener(
            LocaleService localeService,
            PendingRewardQueue pendingRewards,
            MessageRenderer messages,
            boolean dropOverflow,
            Executor mainThreadExecutor,
            Logger logger) {
        this(
                localeService,
                pendingRewards,
                dropOverflow,
                mainThreadExecutor,
                (player, amount) -> messages.send(
                        player, "rewards.pending-delivered", Map.of("amount", amount)),
                logger);
    }

    PlayerLocaleListener(
            LocaleService localeService,
            PendingRewardQueue pendingRewards,
            boolean dropOverflow,
            Executor mainThreadExecutor,
            BiConsumer<Player, Integer> deliveredMessage,
            Logger logger) {
        this.localeService = localeService;
        this.pendingRewards = pendingRewards;
        this.dropOverflow = dropOverflow;
        this.mainThreadExecutor = mainThreadExecutor;
        this.deliveredMessage = deliveredMessage;
        this.logger = logger;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    void handleJoin(Player player) {
        claimWithRetry(player, 1);
    }

    private void claimWithRetry(Player player, int busyRetriesRemaining) {
        AtomicInteger deliveredAmount = new AtomicInteger(-1);
        ClaimResult claim = pendingRewards.claim(player.getUniqueId(), items -> {
            if (items.isEmpty()) {
                return List.of();
            }
            List<ItemStack> attempted = copyItems(items);
            if (!player.isOnline()) {
                return List.copyOf(attempted);
            }

            List<ItemStack> overflowItems = new ArrayList<>();
            for (ItemStack item : items) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                overflowItems.addAll(overflow.values());
            }
            if (dropOverflow) {
                overflowItems.forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }

            DeliveryResult result = calculateDelivery(true, attempted, overflowItems, dropOverflow);
            // 大积压会分多批交付，这里累计每批的实际到账数量。
            deliveredAmount.updateAndGet(previous -> Math.max(previous, 0) + result.deliveredAmount());
            return result.leftovers();
        });
        if (claim.status() == ClaimResult.Status.BUSY) {
            if (busyRetriesRemaining > 0) {
                claim.completion().whenComplete((ignored, failure) -> scheduleMainThread(() -> {
                    if (player.isOnline()) {
                        claimWithRetry(player, busyRetriesRemaining - 1);
                    }
                }));
            }
            return;
        }
        if (claim.status() == ClaimResult.Status.ACCEPTED) {
            claim.completion().whenComplete((ignored, failure) -> {
                int amount = deliveredAmount.get();
                if (amount >= 0) {
                    scheduleMainThread(() -> sendDeliveredMessage(player, amount));
                }
            });
        }
    }

    private void scheduleMainThread(Runnable task) {
        try {
            mainThreadExecutor.execute(task);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "无法调度离线奖励后续主线程任务", exception);
        }
    }

    private void sendDeliveredMessage(Player player, int amount) {
        try {
            deliveredMessage.accept(player, amount);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "发送离线奖励领取消息失败", exception);
        }
    }

    static DeliveryResult calculateDelivery(
            boolean online,
            List<ItemStack> attempted,
            List<ItemStack> overflow,
            boolean dropOverflow) {
        if (!online) {
            return new DeliveryResult(0, copyItems(attempted));
        }
        int attemptedAmount = totalAmount(attempted);
        if (dropOverflow) {
            return new DeliveryResult(attemptedAmount, List.of());
        }
        List<ItemStack> leftovers = copyItems(overflow);
        return new DeliveryResult(Math.max(0, attemptedAmount - totalAmount(leftovers)), leftovers);
    }

    private static int totalAmount(List<ItemStack> items) {
        return items.stream().mapToInt(ItemStack::getAmount).sum();
    }

    private static List<ItemStack> copyItems(List<ItemStack> items) {
        return items.stream().map(ItemStack::clone).toList();
    }

    record DeliveryResult(int deliveredAmount, List<ItemStack> leftovers) {
        DeliveryResult {
            leftovers = List.copyOf(leftovers);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        localeService.remove(event.getPlayer().getUniqueId());
    }
}
