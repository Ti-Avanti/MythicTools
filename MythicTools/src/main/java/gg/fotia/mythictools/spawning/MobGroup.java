package gg.fotia.mythictools.spawning;

import java.util.List;
import java.util.Objects;

/** 可被多个刷怪入口复用的加权怪物组；总权重在构造时预计算。 */
public final class MobGroup {
    private final String id;
    private final int minAmount;
    private final int maxAmount;
    private final List<MobGroupMember> members;
    private final long totalWeight;

    public MobGroup(String id, int minAmount, int maxAmount, List<MobGroupMember> members) {
        this.members = List.copyOf(members);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("怪物组 ID 不能为空");
        }
        if (minAmount <= 0 || maxAmount < minAmount) {
            throw new IllegalArgumentException("怪物组生成数量范围无效");
        }
        if (this.members.isEmpty()) {
            throw new IllegalArgumentException("怪物组成员不能为空");
        }
        this.id = id;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.totalWeight = calculateTotalWeight(this.members);
    }

    public String id() {
        return id;
    }

    public int minAmount() {
        return minAmount;
    }

    public int maxAmount() {
        return maxAmount;
    }

    public List<MobGroupMember> members() {
        return members;
    }

    /** 构造时预计算的成员总权重。 */
    public long totalWeight() {
        return totalWeight;
    }

    private static long calculateTotalWeight(List<MobGroupMember> members) {
        long total = 0L;
        for (MobGroupMember member : members) {
            try {
                total = Math.addExact(total, member.weight());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("怪物组总权重超出 64 位整数范围", exception);
            }
        }
        return total;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MobGroup that
                && Objects.equals(id, that.id)
                && minAmount == that.minAmount
                && maxAmount == that.maxAmount
                && Objects.equals(members, that.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, minAmount, maxAmount, members);
    }

    @Override
    public String toString() {
        return "MobGroup[id=" + id + ", minAmount=" + minAmount
                + ", maxAmount=" + maxAmount + ", members=" + members + "]";
    }
}
