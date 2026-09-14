package gg.fotia.mythictools.config;

import gg.fotia.mythictools.boss.BossRepository;
import gg.fotia.mythictools.boss.BossSnapshot;
import gg.fotia.mythictools.reward.RewardRepository;
import gg.fotia.mythictools.reward.RewardSnapshot;
import gg.fotia.mythictools.spawning.SpawningRepository;
import gg.fotia.mythictools.spawning.SpawningSnapshot;
import gg.fotia.mythictools.leveling.LevelingRepository;
import gg.fotia.mythictools.leveling.LevelingSnapshot;
import java.util.Objects;

/** 按依赖顺序准备各配置域，并通过唯一指针一次性发布。 */
public final class RepositoryReloadCoordinator {
    private final RepositorySnapshotStore store;
    private final RewardRepository rewards;
    private final SpawningRepository spawning;
    private final BossRepository bosses;
    private final LevelingRepository leveling;

    public RepositoryReloadCoordinator(
            RepositorySnapshotStore store,
            RewardRepository rewards,
            SpawningRepository spawning,
            BossRepository bosses) {
        this(store, rewards, spawning, bosses, null);
    }

    public RepositoryReloadCoordinator(RepositorySnapshotStore store, RewardRepository rewards,
            SpawningRepository spawning, BossRepository bosses, LevelingRepository leveling) {
        this.store = Objects.requireNonNull(store, "store");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.spawning = Objects.requireNonNull(spawning, "spawning");
        this.bosses = Objects.requireNonNull(bosses, "bosses");
        this.leveling = leveling;
    }

    public ConfigLoadReport reload(ConfigLoadMode mode) {
        return reload(mode, java.util.EnumSet.allOf(ConfigDomain.class));
    }

    /**
     * 只重新准备指定域，未变更域复用当前已发布快照，仍保持单指针原子发布。
     * Boss 域校验依赖掉落组，因此奖励域变更时会连带重新准备 Boss 域。
     * 距离等级域引用怪物组，因此刷怪域变更时连带重新准备等级域。
     */
    public ConfigLoadReport reload(ConfigLoadMode mode, java.util.Set<ConfigDomain> domains) {
        PreparedSnapshot<RepositorySnapshots> prepared = prepare(mode, domains);
        publish(prepared);
        return prepared.report();
    }

    public void publish(PreparedSnapshot<RepositorySnapshots> prepared) {
        store.publish(prepared.snapshot());
    }

    public PreparedSnapshot<RepositorySnapshots> prepare(ConfigLoadMode mode, java.util.Set<ConfigDomain> domains) {
        RepositorySnapshots current = store.current();
        boolean reloadRewards = domains.contains(ConfigDomain.REWARDS);
        boolean reloadSpawning = domains.contains(ConfigDomain.SPAWNING);
        boolean reloadBosses = domains.contains(ConfigDomain.BOSSES) || reloadRewards;

        RewardSnapshot rewardSnapshot = current.rewards();
        ConfigLoadReport rewardReport = null;
        if (reloadRewards) {
            PreparedSnapshot<RewardSnapshot> candidate = rewards.prepare(mode);
            rewardSnapshot = candidate.snapshot();
            rewardReport = candidate.report();
        }
        SpawningSnapshot spawningSnapshot = current.spawning();
        ConfigLoadReport spawningReport = null;
        if (reloadSpawning) {
            PreparedSnapshot<SpawningSnapshot> candidate = spawning.prepare(mode);
            spawningSnapshot = candidate.snapshot();
            spawningReport = candidate.report();
        }
        BossSnapshot bossSnapshot = current.bosses();
        ConfigLoadReport bossReport = null;
        if (reloadBosses) {
            PreparedSnapshot<BossSnapshot> candidate = bosses.prepare(mode, rewardSnapshot);
            bossSnapshot = candidate.snapshot();
            bossReport = candidate.report();
        }
        LevelingSnapshot levelingSnapshot = current.leveling();
        ConfigLoadReport levelingReport = null;
        if (leveling != null && (reloadSpawning || domains.contains(ConfigDomain.LEVELING))) {
            PreparedSnapshot<LevelingSnapshot> candidate = leveling.prepare(spawningSnapshot);
            levelingSnapshot = candidate.snapshot();
            levelingReport = candidate.report();
        }
        ConfigLoadReport report = ConfigLoadReport.merge(rewardReport, spawningReport, bossReport, levelingReport);
        if (report.blocks(mode)) {
            throw new ConfigLoadException(report);
        }
        return new PreparedSnapshot<>(new RepositorySnapshots(
                rewardSnapshot, spawningSnapshot, bossSnapshot, levelingSnapshot), report);
    }
}
