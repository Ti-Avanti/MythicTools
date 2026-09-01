package gg.fotia.mythictools.boss;

import java.util.List;
import java.util.Map;
import gg.fotia.mythictools.reward.FirstDefeatRewardConfig;

/** Boss 伤害排名与击杀者奖励配置。 */
public record BossRewardConfig(
        boolean rankingEnabled,
        int maxRecipients,
        Map<Integer, List<GroupReward>> rankRewards,
        boolean rankingChatEnabled,
        boolean killerEnabled,
        List<GroupReward> killerRewards,
        FirstDefeatRewardConfig firstDefeatRewards) {
    public BossRewardConfig {
        rankRewards = Map.copyOf(rankRewards);
        killerRewards = List.copyOf(killerRewards);
        if (firstDefeatRewards == null) {
            firstDefeatRewards = FirstDefeatRewardConfig.disabled();
        }
    }
}
