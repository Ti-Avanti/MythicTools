package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.ConfigValues;
import gg.fotia.mythictools.config.SafetyLimits;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/** 怪物掉落规则中的单个掉落组分配草稿。 */
final class MobDropGroupDraft {
    private final int sourceIndex;
    private final String groupId;
    private long weight;
    private int minimum;
    private int maximum;

    private MobDropGroupDraft(int sourceIndex, String groupId, long weight, int minimum, int maximum) {
        this.sourceIndex = sourceIndex;
        this.groupId = requireGroupId(groupId);
        this.weight = weight;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    static MobDropGroupDraft create(String groupId) {
        return new MobDropGroupDraft(-1, groupId, 100L, 1, 1);
    }

    static MobDropGroupDraft load(YamlConfiguration yaml, int index) {
        List<Map<?, ?>> groups = yaml.getMapList("groups");
        if (index < 0 || index >= groups.size()) {
            throw new IllegalArgumentException("掉落组分配不存在: " + index);
        }
        Map<?, ?> raw = groups.get(index);
        return new MobDropGroupDraft(
                index,
                stringValue(raw.get("id")),
                ConfigValues.longValueOrDefault(raw.get("weight"), 1L,
                        "groups.weight", 1L, Long.MAX_VALUE),
                ConfigValues.intValueOrDefault(raw.get("min-amount"), 0,
                        "groups.min-amount", 0, Integer.MAX_VALUE),
                ConfigValues.intValueOrDefault(raw.get("max-amount"), 1,
                        "groups.max-amount", 0, Integer.MAX_VALUE));
    }

    static List<String> groupIds(YamlConfiguration yaml) {
        return yaml.getMapList("groups").stream()
                .map(group -> stringValue(group.get("id")).trim())
                .filter(id -> !id.isEmpty())
                .toList();
    }

    String groupId() {
        return groupId;
    }

    long weight() {
        return weight;
    }

    void weight(long value) {
        weight = value;
    }

    int minimum() {
        return minimum;
    }

    void minimum(int value) {
        minimum = value;
    }

    int maximum() {
        return maximum;
    }

    void maximum(int value) {
        maximum = value;
    }

    void validate(SafetyLimits limits) {
        GuiWeightRules.requireValid(weight, limits);
        if (minimum < 0 || maximum < 0
                || minimum > limits.maxDropsPerMob() || maximum > limits.maxDropsPerMob()) {
            throw new IllegalArgumentException(
                    "抽取数量必须在 0 到 " + limits.maxDropsPerMob() + " 之间");
        }
        ConfigValues.requireOrdered(minimum, maximum, "groups.min-amount", "groups.max-amount");
    }

    void applyTo(YamlConfiguration yaml) {
        List<Map<String, Object>> groups = mutableGroups(yaml);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", groupId);
        value.put("weight", weight);
        value.put("min-amount", minimum);
        value.put("max-amount", maximum);
        if (sourceIndex < 0) {
            groups.add(value);
        } else {
            groups.set(resolveSourceIndex(groups), value);
        }
        yaml.set("groups", groups);
    }

    private int resolveSourceIndex(List<Map<String, Object>> groups) {
        if (sourceIndex < groups.size() && groupId.equals(stringValue(groups.get(sourceIndex).get("id")))) {
            return sourceIndex;
        }
        int found = -1;
        for (int index = 0; index < groups.size(); index++) {
            if (!groupId.equals(stringValue(groups.get(index).get("id")))) {
                continue;
            }
            if (found >= 0) {
                throw new IllegalStateException("掉落组分配位置发生冲突: " + groupId);
            }
            found = index;
        }
        if (found < 0) {
            throw new IllegalStateException("掉落组分配已被移除: " + groupId);
        }
        return found;
    }

    static void remove(YamlConfiguration yaml, int index) {
        List<Map<String, Object>> groups = mutableGroups(yaml);
        if (index < 0 || index >= groups.size()) {
            throw new IllegalArgumentException("掉落组分配不存在: " + index);
        }
        groups.remove(index);
        yaml.set("groups", groups);
    }

    private static List<Map<String, Object>> mutableGroups(YamlConfiguration yaml) {
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<?, ?> source : yaml.getMapList("groups")) {
            Map<String, Object> target = new LinkedHashMap<>();
            source.forEach((key, value) -> target.put(String.valueOf(key), value));
            copied.add(target);
        }
        return copied;
    }

    private static String requireGroupId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("掉落组 ID 不能为空");
        }
        return value.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
