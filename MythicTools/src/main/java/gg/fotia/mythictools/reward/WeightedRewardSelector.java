package gg.fotia.mythictools.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToLongFunction;

/** 实现严格总数限制的两层权重奖励抽取。 */
public final class WeightedRewardSelector {
    private final RewardRepository repository;

    public WeightedRewardSelector(RewardRepository repository) {
        this.repository = repository;
    }

    /** 按怪物规则抽取最终奖励条目。 */
    public List<RewardGrant> select(MobDropRule rule) {
        List<GroupCapacity> capacities = new ArrayList<>();
        for (DropGroupRef ref : rule.groups()) {
            int capacity = randomBetween(ref.minAmount(), ref.maxAmount());
            if (capacity > 0 && repository.group(ref.groupId()).isPresent()) {
                capacities.add(new GroupCapacity(ref, capacity));
            }
        }
        List<RewardGrant> result = new ArrayList<>();
        while (result.size() < rule.maxDrops()) {
            List<GroupCapacity> available = capacities.stream()
                    .filter(capacity -> capacity.remaining > 0)
                    .toList();
            if (available.isEmpty()) {
                break;
            }
            GroupCapacity selectedGroup = weighted(available, capacity -> capacity.reference.weight());
            DropGroup group = repository.group(selectedGroup.reference.groupId()).orElseThrow();
            if (group.positiveEntries().isEmpty()) {
                selectedGroup.remaining = 0;
                continue;
            }
            RewardEntry entry = weightedEntry(group);
            result.add(new RewardGrant(entry, randomBetween(entry.minAmount(), entry.maxAmount())));
            selectedGroup.remaining--;
        }
        return List.copyOf(result);
    }

    /** 将指定掉落组抽取若干份，供 Boss 名次和击杀奖励复用。 */
    public List<RewardGrant> selectGroup(String groupId, int copies) {
        if (copies < 0) {
            throw new IllegalArgumentException("copies 不能小于 0");
        }
        DropGroup group = repository.group(groupId)
                .orElseThrow(() -> new IllegalArgumentException("不存在的掉落组: " + groupId));
        if (group.positiveEntries().isEmpty()) {
            return List.of();
        }
        List<RewardGrant> result = new ArrayList<>();
        for (int index = 0; index < copies; index++) {
            RewardEntry entry = weightedEntry(group);
            result.add(new RewardGrant(entry, randomBetween(entry.minAmount(), entry.maxAmount())));
        }
        return List.copyOf(result);
    }

    /** 直接构造首次击败专用条目，完全绕过权重抽取并对重复引用去重。 */
    public List<RewardGrant> selectFirstDefeat(List<RewardEntryRef> references) {
        List<RewardGrant> result = new ArrayList<>();
        for (RewardEntryRef reference : new LinkedHashSet<>(references)) {
            RewardEntry entry = repository.entry(reference.groupId(), reference.entryId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "不存在的首次击败奖励: " + reference.groupId() + "/" + reference.entryId()));
            if (entry.grantMode() != RewardGrantMode.FIRST_DEFEAT) {
                throw new IllegalArgumentException(
                        "奖励不是首次击败专用模式: " + reference.groupId() + "/" + reference.entryId());
            }
            result.add(new RewardGrant(entry, randomBetween(entry.minAmount(), entry.maxAmount())));
        }
        return List.copyOf(result);
    }

    /** 使用掉落组构造期预计算的正权重条目与总权重抽取一条奖励。 */
    private static RewardEntry weightedEntry(DropGroup group) {
        return pick(group.positiveEntries(), RewardEntry::weight,
                ThreadLocalRandom.current().nextLong(group.positiveTotalWeight()));
    }

    private static int randomBetween(int minimum, int maximum) {
        if (minimum == maximum) {
            return minimum;
        }
        long selected = ThreadLocalRandom.current().nextLong((long) minimum, (long) maximum + 1L);
        return Math.toIntExact(selected);
    }

    private static <T> T weighted(List<T> values, ToLongFunction<T> weight) {
        long total = totalWeight(values, weight);
        return pick(values, weight, ThreadLocalRandom.current().nextLong(total));
    }

    static <T> T weightedAt(List<T> values, ToLongFunction<T> weight, long ticket) {
        long total = totalWeight(values, weight);
        if (ticket < 0L || ticket >= total) {
            throw new IllegalArgumentException("权重票号超出范围");
        }
        return pick(values, weight, ticket);
    }

    private static <T> T pick(List<T> values, ToLongFunction<T> weight, long ticket) {
        long target = ticket;
        for (T value : values) {
            target -= weight.applyAsLong(value);
            if (target < 0L) {
                return value;
            }
        }
        throw new IllegalStateException("奖励权重计算失败");
    }

    private static <T> long totalWeight(List<T> values, ToLongFunction<T> weight) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("权重候选不能为空");
        }
        long total = 0L;
        for (T value : values) {
            long current = weight.applyAsLong(value);
            if (current <= 0L) {
                throw new IllegalArgumentException("权重必须大于 0");
            }
            try {
                total = Math.addExact(total, current);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("奖励总权重超出 64 位整数范围", exception);
            }
        }
        return total;
    }

    private static final class GroupCapacity {
        private final DropGroupRef reference;
        private int remaining;

        private GroupCapacity(DropGroupRef reference, int remaining) {
            this.reference = reference;
            this.remaining = remaining;
        }
    }
}
