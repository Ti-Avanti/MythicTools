package gg.fotia.mythictools.reward;

import java.util.List;

/** 一个 Boss 或普通 MythicMob 的首次击败奖励规则。 */
public record FirstDefeatRewardConfig(
        boolean enabled,
        FirstDefeatScope scope,
        FirstDefeatRecipient recipient,
        List<RewardEntryRef> entries) {
    public FirstDefeatRewardConfig {
        if (scope == null || recipient == null) {
            throw new IllegalArgumentException("首次击败范围与接收者不能为空");
        }
        entries = List.copyOf(entries);
        if (enabled && entries.isEmpty()) {
            throw new IllegalArgumentException("启用首次击败奖励时 entries 不能为空");
        }
        if (scope == FirstDefeatScope.SERVER && recipient == FirstDefeatRecipient.PARTICIPANTS) {
            throw new IllegalArgumentException("全服首次奖励只能发给最终击杀者");
        }
    }

    public static FirstDefeatRewardConfig disabled() {
        return new FirstDefeatRewardConfig(
                false, FirstDefeatScope.PLAYER, FirstDefeatRecipient.KILLER, List.of());
    }
}
