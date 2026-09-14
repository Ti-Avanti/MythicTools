package gg.fotia.mythictools.leveling;

import java.util.List;
import java.util.Set;

/** 一个等级规则组的已解析怪物集合、距离来源和曲线。 */
public record LevelRule(String id, int priority, Set<String> mobIds,
                        List<DistanceOrigin> origins, DistanceCurve curve) {
    public LevelRule {
        mobIds = Set.copyOf(mobIds);
        origins = List.copyOf(origins);
    }
}
