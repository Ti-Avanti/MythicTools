package gg.fotia.mythictools.spawning;

import java.util.concurrent.ThreadLocalRandom;

/** 怪物组权重抽取器。 */
public final class WeightedMobSelector {
    private WeightedMobSelector() {
    }

    public static MobGroupMember select(MobGroup group) {
        return selectAt(group, ThreadLocalRandom.current().nextLong(group.totalWeight()));
    }

    static MobGroupMember selectAt(MobGroup group, long ticket) {
        long total = group.totalWeight();
        if (ticket < 0L || ticket >= total) {
            throw new IllegalArgumentException("权重票号超出范围");
        }
        long cursor = 0L;
        for (MobGroupMember member : group.members()) {
            cursor += member.weight();
            if (ticket < cursor) {
                return member;
            }
        }
        throw new IllegalStateException("怪物组权重计算失败");
    }
}
