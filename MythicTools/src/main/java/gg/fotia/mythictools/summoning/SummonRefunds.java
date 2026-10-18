package gg.fotia.mythictools.summoning;

import gg.fotia.mythictools.storage.PendingRewardQueue;
import gg.fotia.mythictools.storage.QueueResult;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** 主线程返还背包，离线及溢出部分交给现有持久奖励队列。 */
public final class SummonRefunds {
    private final PendingRewardQueue pending;
    private final Logger logger;
    public SummonRefunds(PendingRewardQueue pending, Logger logger) { this.pending = pending; this.logger = logger; }

    public void give(UUID id, ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0) return;
        var player = Bukkit.getPlayer(id);
        List<ItemStack> remainder = player == null || !player.isOnline() || player.isDead()
                ? List.of(stack.clone()) : List.copyOf(player.getInventory().addItem(stack.clone()).values());
        if (remainder.isEmpty()) return;
        pending.queue(id, remainder).whenComplete((result, error) -> {
            if (error != null || result != QueueResult.STORED) {
                pending.retainForRetry(id, remainder).whenComplete((retry, failure) -> {
                    if (failure != null || retry != QueueResult.STORED) {
                        logger.severe("召唤物返还持久化失败，玩家 " + id + "，物品 " + remainder);
                    }
                });
            }
        });
    }
}
