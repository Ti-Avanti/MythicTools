package gg.fotia.mythictools.reward;

/** 对掉落组中某个确定奖励条目的引用。 */
public record RewardEntryRef(String groupId, String entryId) {
    public RewardEntryRef {
        if (groupId == null || groupId.isBlank() || entryId == null || entryId.isBlank()) {
            throw new IllegalArgumentException("奖励引用的掉落组和条目 ID 不能为空");
        }
    }
}
