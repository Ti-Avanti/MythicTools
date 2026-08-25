package gg.fotia.mythictools.boss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Boss 配置的不可变快照。 */
public final class BossSnapshot {
    private final Map<String, BossConfig> bosses;

    public BossSnapshot(Map<String, BossConfig> bosses) {
        Map<String, BossConfig> copied = new LinkedHashMap<>();
        bosses.forEach((id, boss) -> copied.put(id, copyBoss(boss)));
        this.bosses = Collections.unmodifiableMap(copied);
    }

    public static BossSnapshot empty() {
        return new BossSnapshot(Map.of());
    }

    public Collection<BossConfig> bosses() {
        return List.copyOf(bosses.values());
    }

    public Collection<String> bossIds() {
        return List.copyOf(bosses.keySet());
    }

    public BossConfig boss(String id) {
        return bosses.get(id);
    }

    private static BossConfig copyBoss(BossConfig value) {
        List<BossPhase> phases = value.phases().stream()
                .map(phase -> new BossPhase(phase.mobId(), phase.level()))
                .toList();
        BossPhase nativePhase = value.mythicNativePhase() == null ? null
                : new BossPhase(value.mythicNativePhase().mobId(), value.mythicNativePhase().level());
        List<BossSpawner> spawners = value.spawners().stream().map(BossSnapshot::copySpawner).toList();
        return new BossConfig(
                value.id(), orderedMap(value.displays()), value.phaseMode(), phases, nativePhase,
                value.lootPolicy(), spawners,
                new BossBroadcast(orderedMap(value.spawnBroadcast().messages()), value.spawnBroadcast().commands()),
                new BossBroadcast(orderedMap(value.deathBroadcast().messages()), value.deathBroadcast().commands()),
                copyRewards(value.rewards()));
    }

    private static BossSpawner copySpawner(BossSpawner value) {
        BossPointSchedule schedule = value.pointSchedule() == null ? null
                : new BossPointSchedule(
                        value.pointSchedule().fallbackIntervalSeconds(),
                        value.pointSchedule().zoneId(),
                        value.pointSchedule().timeWindows());
        return new BossSpawner(
                value.id(), value.type(), value.enabled(), value.intervalSeconds(), value.chance(), value.maxActive(),
                value.minimumOnlinePlayers(), schedule, value.worlds(), value.biomes(), value.minDistance(),
                value.maxDistance(), value.location());
    }

    private static BossRewardConfig copyRewards(BossRewardConfig value) {
        Map<Integer, List<GroupReward>> ranks = new LinkedHashMap<>();
        value.rankRewards().forEach((rank, groups) -> ranks.put(rank, List.copyOf(groups)));
        return new BossRewardConfig(
                value.rankingEnabled(), value.maxRecipients(), ranks, value.rankingChatEnabled(),
                value.killerEnabled(), new ArrayList<>(value.killerRewards()));
    }

    private static Map<String, String> orderedMap(Map<String, String> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
