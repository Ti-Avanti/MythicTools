package gg.fotia.mythictools.reward;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 奖励配置的不可变快照，保留 YAML 文件与节点的迭代顺序。 */
public final class RewardSnapshot {
    private final Map<String, Rarity> rarities;
    private final Map<String, DropGroup> groups;
    private final Map<String, MobDropRule> mobRules;

    public RewardSnapshot(
            Map<String, Rarity> rarities,
            Map<String, DropGroup> groups,
            Map<String, MobDropRule> mobRules) {
        this.rarities = immutableCopy(rarities, RewardSnapshot::copyRarity);
        this.groups = immutableCopy(groups, RewardSnapshot::copyGroup);
        this.mobRules = immutableCopy(mobRules, RewardSnapshot::copyMobRule);
    }

    public static RewardSnapshot empty() {
        return new RewardSnapshot(Map.of(), Map.of(), Map.of());
    }

    public Optional<Rarity> rarity(String id) {
        return Optional.ofNullable(rarities.get(id));
    }

    public Optional<DropGroup> group(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    public Optional<RewardEntry> entry(String groupId, String entryId) {
        return group(groupId).flatMap(group -> group.entries().stream()
                .filter(entry -> entry.id().equals(entryId)).findFirst());
    }

    public Optional<MobDropRule> mobRule(String id) {
        return Optional.ofNullable(mobRules.get(id));
    }

    public Collection<Rarity> rarities() {
        return List.copyOf(rarities.values());
    }

    public Collection<String> groupIds() {
        return List.copyOf(groups.keySet());
    }

    public Collection<String> mobIds() {
        return List.copyOf(mobRules.keySet());
    }

    private static Rarity copyRarity(Rarity value) {
        return new Rarity(value.id(), orderedMap(value.displays()), value.color(), value.priority());
    }

    private static DropGroup copyGroup(DropGroup value) {
        List<RewardEntry> entries = new ArrayList<>();
        for (RewardEntry entry : value.entries()) {
            entries.add(new RewardEntry(
                    entry.id(), entry.type(), entry.grantMode(), entry.weight(),
                    entry.minAmount(), entry.maxAmount(),
                    entry.rarityId(), orderedMap(entry.displays()), orderedMap(entry.messages()),
                    entry.item(), entry.delivery(), entry.command(), entry.commandExecutor()));
        }
        return new DropGroup(value.id(), entries);
    }

    private static MobDropRule copyMobRule(MobDropRule value) {
        return new MobDropRule(
                value.mobId(), value.maxDrops(), value.minExperience(), value.maxExperience(), value.groups(),
                value.firstDefeatRewards());
    }

    private static <T> Map<String, T> immutableCopy(
            Map<String, T> source,
            java.util.function.Function<T, T> copier) {
        Map<String, T> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, copier.apply(value)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> orderedMap(Map<String, String> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
