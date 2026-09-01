package gg.fotia.mythictools.reward;

import java.util.List;
import java.util.Objects;

/** 一组按权重抽取的奖励条目；正权重子集与总权重在构造时预计算。 */
public final class DropGroup {
    private final String id;
    private final List<RewardEntry> entries;
    private final List<RewardEntry> positiveEntries;
    private final long positiveTotalWeight;

    public DropGroup(String id, List<RewardEntry> entries) {
        this.id = id;
        this.entries = List.copyOf(entries);
        List<RewardEntry> positives = this.entries.stream()
                .filter(entry -> entry.grantMode() == RewardGrantMode.WEIGHTED)
                .filter(entry -> entry.weight() > 0L)
                .toList();
        long total = 0L;
        for (RewardEntry entry : positives) {
            try {
                total = Math.addExact(total, entry.weight());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("奖励总权重超出 64 位整数范围", exception);
            }
        }
        this.positiveEntries = positives;
        this.positiveTotalWeight = total;
    }

    public String id() {
        return id;
    }

    public List<RewardEntry> entries() {
        return entries;
    }

    /** 权重大于 0 的条目子集。 */
    public List<RewardEntry> positiveEntries() {
        return positiveEntries;
    }

    /** positiveEntries 的总权重；没有正权重条目时为 0。 */
    public long positiveTotalWeight() {
        return positiveTotalWeight;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DropGroup that
                && Objects.equals(id, that.id)
                && Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, entries);
    }

    @Override
    public String toString() {
        return "DropGroup[id=" + id + ", entries=" + entries + "]";
    }
}
