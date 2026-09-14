package gg.fotia.mythictools.leveling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 已发布的等级配置，按怪物 ID 建索引；始终先比较距离，再处理同距优先级。 */
public final class LevelingSnapshot {
    private final Map<String, DistanceOrigin> points;
    private final Map<String, DistanceOrigin> regions;
    private final Map<String, List<LevelRule>> rulesByMob;

    public LevelingSnapshot(Map<String, DistanceOrigin> points, Map<String, DistanceOrigin> regions,
                            List<LevelRule> rules) {
        this.points = Map.copyOf(points);
        this.regions = Map.copyOf(regions);
        Map<String, List<LevelRule>> byMob = new HashMap<>();
        for (LevelRule rule : rules) {
            rule.mobIds().forEach(mob -> byMob.computeIfAbsent(mob, ignored -> new ArrayList<>()).add(rule));
        }
        byMob.replaceAll((mob, matching) -> List.copyOf(matching));
        rulesByMob = Map.copyOf(byMob);
    }

    public static LevelingSnapshot empty() {
        return new LevelingSnapshot(Map.of(), Map.of(), List.of());
    }

    public List<String> pointIds() { return points.keySet().stream().sorted().toList(); }
    public List<String> regionIds() { return regions.keySet().stream().sorted().toList(); }

    public Optional<Result> resolve(String mobId, String world, double x, double z,
                                     double originalLevel, RegionLookup regionLookup) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(originalLevel)) {
            return Optional.empty();
        }
        LevelRule nearestRule = null;
        DistanceOrigin nearestOrigin = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (LevelRule rule : rulesByMob.getOrDefault(mobId, List.of())) {
            for (DistanceOrigin origin : rule.origins()) {
                if (!origin.enabled() || !origin.world().equals(world)) {
                    continue;
                }
                HorizontalShape shape = origin.shape(regionLookup).orElse(null);
                if (shape == null || shape.lowerBoundSquared(x, z) > nearest) {
                    continue;
                }
                double distance = shape.distanceSquared(x, z);
                if (distance < nearest || (Double.compare(distance, nearest) == 0
                        && preferred(rule, origin, nearestRule, nearestOrigin))) {
                    nearest = distance;
                    nearestRule = rule;
                    nearestOrigin = origin;
                }
            }
        }
        if (nearestRule == null) {
            return Optional.empty();
        }
        double distance = Math.sqrt(nearest);
        return Optional.of(new Result(nearestRule.id(), nearestOrigin.id(), distance,
                nearestRule.curve().level(distance, originalLevel)));
    }

    private static boolean preferred(LevelRule rule, DistanceOrigin origin,
                                     LevelRule current, DistanceOrigin currentOrigin) {
        if (current == null || rule.priority() != current.priority()) {
            return current == null || rule.priority() > current.priority();
        }
        int order = rule.id().compareTo(current.id());
        return order < 0 || (order == 0 && origin.id().compareTo(currentOrigin.id()) < 0);
    }

    public record Result(String ruleId, String originId, double distance, double level) {
    }
}
