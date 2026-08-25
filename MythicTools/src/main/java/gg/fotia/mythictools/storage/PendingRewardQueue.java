package gg.fotia.mythictools.storage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.inventory.ItemStack;

/** 保存并可靠领取玩家待交付物品的接口。 */
public interface PendingRewardQueue {
    CompletionStage<QueueResult> queue(UUID playerId, List<ItemStack> items);

    /** 使用与 queue 相同的持久接管路径保留重试物品，并返回明确的接管结果。 */
    CompletionStage<QueueResult> retainForRetry(UUID playerId, List<ItemStack> items);

    /**
     * 接纳一次领取。背包变更发生在数据库结算之前，因此进程在二者之间崩溃时语义为至少一次；
     * 若没有写入物品收据，无法跨该边界实现严格恰好一次。
     */
    ClaimResult claim(UUID playerId, PendingRewardDelivery delivery);
}
