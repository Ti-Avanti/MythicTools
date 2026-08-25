package gg.fotia.mythictools.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 玩家编辑会话与全部子编辑草稿的集中存储。 */
final class GuiSessions {
    final Map<UUID, EditorSession> editors = new HashMap<>();
    final Map<UUID, RewardEntryDraft> rewardDrafts = new HashMap<>();
    final Map<UUID, MobMemberDraft> mobMemberDrafts = new HashMap<>();
    final Map<UUID, MobDropGroupDraft> mobDropGroupDrafts = new HashMap<>();
    final Map<UUID, BossPhaseDraft> bossPhaseDrafts = new HashMap<>();
    final Map<UUID, BossRewardGroupDraft> bossRewardGroupDrafts = new HashMap<>();
    final Map<UUID, BossTimeWindowDraft> timeWindowDrafts = new HashMap<>();

    List<Map<UUID, ?>> draftMaps() {
        return List.of(rewardDrafts, mobMemberDrafts, mobDropGroupDrafts,
                bossPhaseDrafts, bossRewardGroupDrafts, timeWindowDrafts);
    }

    void clearDrafts(UUID playerId) {
        draftMaps().forEach(drafts -> drafts.remove(playerId));
    }

    void clearAllDrafts() {
        draftMaps().forEach(Map::clear);
    }
}
