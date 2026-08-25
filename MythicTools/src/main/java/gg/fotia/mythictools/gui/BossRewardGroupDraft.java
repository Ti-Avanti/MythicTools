package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.ConfigValues;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/** Boss 某个接收目标中的单条掉落组奖励草稿。 */
final class BossRewardGroupDraft {
    private final int sourceIndex;
    private final String sourceGroupId;
    private final int sourceCopies;
    private String groupId;
    private int copies;

    private BossRewardGroupDraft(
            int sourceIndex, String sourceGroupId, int sourceCopies, String groupId, int copies) {
        this.sourceIndex = sourceIndex;
        this.sourceGroupId = sourceGroupId;
        this.sourceCopies = sourceCopies;
        this.groupId = requireGroupId(groupId);
        this.copies = copies;
    }

    static BossRewardGroupDraft create(String groupId) {
        return new BossRewardGroupDraft(-1, null, -1, groupId, 1);
    }

    static BossRewardGroupDraft load(YamlConfiguration yaml, String path, int index) {
        List<Map<?, ?>> groups = yaml.getMapList(path);
        if (index < 0 || index >= groups.size()) {
            throw new IllegalArgumentException("Boss 奖励组不存在: " + index);
        }
        Map<?, ?> raw = groups.get(index);
        String groupId = String.valueOf(raw.get("group"));
        int copies = ConfigValues.intValueOrDefault(raw.get("copies"), 1,
                path + ".copies", 1, Integer.MAX_VALUE);
        return new BossRewardGroupDraft(index, groupId, copies, groupId, copies);
    }

    String groupId() {
        return groupId;
    }

    void groupId(String value) {
        groupId = requireGroupId(value);
    }

    int copies() {
        return copies;
    }

    void copies(int value) {
        copies = value;
    }

    void applyTo(YamlConfiguration yaml, String path) {
        List<Map<String, Object>> groups = mutableGroups(yaml, path);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("group", groupId);
        value.put("copies", copies);
        if (sourceIndex < 0) {
            groups.add(value);
        } else {
            groups.set(resolveSourceIndex(groups), value);
        }
        yaml.set(path, groups);
    }

    private int resolveSourceIndex(List<Map<String, Object>> groups) {
        if (sourceIndex < groups.size() && matchesSource(groups.get(sourceIndex))) {
            return sourceIndex;
        }
        int found = -1;
        for (int index = 0; index < groups.size(); index++) {
            if (!matchesSource(groups.get(index))) {
                continue;
            }
            if (found >= 0) {
                throw new IllegalStateException("Boss 奖励组位置发生冲突，请重新打开奖励列表");
            }
            found = index;
        }
        if (found < 0) {
            throw new IllegalStateException("Boss 奖励组已被外部修改或移除: " + sourceGroupId);
        }
        return found;
    }

    private boolean matchesSource(Map<String, Object> candidate) {
        return sourceGroupId.equals(String.valueOf(candidate.get("group")))
                && sourceCopies == ConfigValues.intValueOrDefault(
                        candidate.get("copies"), 1, "copies", 1, Integer.MAX_VALUE);
    }

    static void remove(YamlConfiguration yaml, String path, int index) {
        List<Map<String, Object>> groups = mutableGroups(yaml, path);
        if (index < 0 || index >= groups.size()) {
            throw new IllegalArgumentException("Boss 奖励组不存在: " + index);
        }
        groups.remove(index);
        yaml.set(path, groups);
    }

    private static List<Map<String, Object>> mutableGroups(YamlConfiguration yaml, String path) {
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<?, ?> source : yaml.getMapList(path)) {
            Map<String, Object> target = new LinkedHashMap<>();
            source.forEach((key, value) -> target.put(String.valueOf(key), value));
            copied.add(target);
        }
        return copied;
    }

    private static String requireGroupId(String value) {
        if (value == null || value.isBlank() || value.equals("null")) {
            throw new IllegalArgumentException("掉落组 ID 不能为空");
        }
        return value.trim();
    }
}
