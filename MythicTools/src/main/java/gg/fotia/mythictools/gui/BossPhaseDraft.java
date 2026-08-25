package gg.fotia.mythictools.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/** death-respawn Boss 单个阶段的隔离编辑草稿与有序列表操作。 */
final class BossPhaseDraft {
    private final int sourceIndex;
    private final String sourceMobId;
    private final double sourceLevel;
    private String mobId;
    private double level;

    private BossPhaseDraft(
            int sourceIndex, String sourceMobId, double sourceLevel, String mobId, double level) {
        this.sourceIndex = sourceIndex;
        this.sourceMobId = sourceMobId;
        this.sourceLevel = sourceLevel;
        mobId(mobId);
        level(level);
    }

    static BossPhaseDraft create(String defaultMobId) {
        return new BossPhaseDraft(-1, null, Double.NaN, defaultMobId, 1.0D);
    }

    static BossPhaseDraft load(YamlConfiguration yaml, int index) {
        List<Map<?, ?>> phases = yaml.getMapList("phases");
        requireIndex(index, phases.size());
        Map<?, ?> phase = phases.get(index);
        Object rawMob = phase.get("mob");
        Object rawLevel = phase.get("level");
        if (rawLevel != null && !(rawLevel instanceof Number)) {
            throw new IllegalArgumentException("Boss 阶段等级必须是数字");
        }
        double level = rawLevel == null ? 1.0D : ((Number) rawLevel).doubleValue();
        String mobId = rawMob == null ? "" : String.valueOf(rawMob);
        return new BossPhaseDraft(index, mobId, level, mobId, level);
    }

    static int count(YamlConfiguration yaml) {
        return yaml.getMapList("phases").size();
    }

    String mobId() {
        return mobId;
    }

    void mobId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MythicMob ID 不能为空");
        }
        mobId = value.trim();
    }

    double level() {
        return level;
    }

    void level(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException("Boss 等级必须是有限正数");
        }
        level = value;
    }

    int applyTo(YamlConfiguration yaml, int index) {
        List<BossPhaseDraft> phases = loadAll(yaml);
        BossPhaseDraft replacement = new BossPhaseDraft(-1, null, Double.NaN, mobId, level);
        int targetIndex;
        if (sourceIndex < 0) {
            if (index != phases.size()) {
                throw new IllegalStateException("Boss 阶段列表已发生变化，请重新新增阶段");
            }
            phases.add(replacement);
            targetIndex = index;
        } else {
            targetIndex = resolveSourceIndex(phases);
            phases.set(targetIndex, replacement);
        }
        write(yaml, phases);
        return targetIndex;
    }

    private int resolveSourceIndex(List<BossPhaseDraft> phases) {
        if (sourceIndex < phases.size() && matchesSource(phases.get(sourceIndex))) {
            return sourceIndex;
        }
        int found = -1;
        for (int index = 0; index < phases.size(); index++) {
            if (!matchesSource(phases.get(index))) {
                continue;
            }
            if (found >= 0) {
                throw new IllegalStateException("Boss 阶段位置发生冲突，请重新打开阶段列表");
            }
            found = index;
        }
        if (found < 0) {
            throw new IllegalStateException("Boss 阶段已被外部修改或移除，请重新打开阶段列表");
        }
        return found;
    }

    private boolean matchesSource(BossPhaseDraft candidate) {
        return sourceMobId.equals(candidate.mobId)
                && Double.compare(sourceLevel, candidate.level) == 0;
    }

    static void remove(YamlConfiguration yaml, int index) {
        List<BossPhaseDraft> phases = loadAll(yaml);
        requireIndex(index, phases.size());
        if (phases.size() <= 1) {
            throw new IllegalStateException("Boss 必须至少保留一个阶段");
        }
        phases.remove(index);
        write(yaml, phases);
    }

    static int move(YamlConfiguration yaml, int index, int offset) {
        if (offset != -1 && offset != 1) {
            throw new IllegalArgumentException("Boss 阶段每次只能上移或下移一位");
        }
        List<BossPhaseDraft> phases = loadAll(yaml);
        requireIndex(index, phases.size());
        int target = index + offset;
        if (target < 0 || target >= phases.size()) {
            return index;
        }
        BossPhaseDraft moving = phases.remove(index);
        phases.add(target, moving);
        write(yaml, phases);
        return target;
    }

    private static List<BossPhaseDraft> loadAll(YamlConfiguration yaml) {
        List<Map<?, ?>> raw = yaml.getMapList("phases");
        List<BossPhaseDraft> phases = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            phases.add(load(yaml, index));
        }
        return phases;
    }

    private static void write(YamlConfiguration yaml, List<BossPhaseDraft> phases) {
        List<Map<String, Object>> values = new ArrayList<>(phases.size());
        for (BossPhaseDraft phase : phases) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("mob", phase.mobId);
            value.put("level", phase.level);
            values.add(value);
        }
        yaml.set("phases", values);
    }

    private static void requireIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Boss 阶段序号超出范围: " + index);
        }
    }
}
