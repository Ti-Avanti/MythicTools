package gg.fotia.mythictools.storage;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/** 在主线程交付待领取物品，并返回仍未交付的物品。 */
@FunctionalInterface
public interface PendingRewardDelivery {
    List<ItemStack> deliver(List<ItemStack> items);
}
