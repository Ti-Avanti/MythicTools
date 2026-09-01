package gg.fotia.mythictools.reward;

/** GUI 可选择的首次击败专用奖励。 */
public record FirstDefeatRewardOption(String groupId, RewardEntry entry) {
    public RewardEntryRef reference() {
        return new RewardEntryRef(groupId, entry.id());
    }
}
