package gg.fotia.mythictools.reward;

import java.util.Map;
import org.bukkit.inventory.ItemStack;

/** 掉落组中的单个可抽取奖励。 */
public record RewardEntry(
        String id,
        RewardType type,
        RewardGrantMode grantMode,
        long weight,
        int minAmount,
        int maxAmount,
        String rarityId,
        Map<String, String> displays,
        Map<String, String> messages,
        ItemStack item,
        RewardDelivery delivery,
        String command,
        CommandExecutorType commandExecutor) {

    public RewardEntry {
        displays = Map.copyOf(displays);
        messages = Map.copyOf(messages);
        item = item == null ? null : item.clone();
    }

    /** 获取指定语言的奖励名称。 */
    public String display(String locale) {
        return displays.getOrDefault(locale, displays.getOrDefault("zh_CN", id));
    }

    /** 获取指定语言的获得消息，空字符串表示不提示。 */
    public String message(String locale) {
        return messages.getOrDefault(locale, messages.getOrDefault("zh_CN", ""));
    }

    @Override
    public ItemStack item() {
        return item == null ? null : item.clone();
    }
}
