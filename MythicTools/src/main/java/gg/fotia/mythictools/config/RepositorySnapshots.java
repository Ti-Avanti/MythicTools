package gg.fotia.mythictools.config;

import gg.fotia.mythictools.boss.BossSnapshot;
import gg.fotia.mythictools.reward.RewardSnapshot;
import gg.fotia.mythictools.spawning.SpawningSnapshot;
import gg.fotia.mythictools.leveling.LevelingSnapshot;
import java.util.Objects;

/** 三个配置域在同一发布时刻对应的快照束。 */
public record RepositorySnapshots(
        RewardSnapshot rewards,
        SpawningSnapshot spawning,
        BossSnapshot bosses,
        LevelingSnapshot leveling) {

    public RepositorySnapshots {
        Objects.requireNonNull(rewards, "rewards");
        Objects.requireNonNull(spawning, "spawning");
        Objects.requireNonNull(bosses, "bosses");
        Objects.requireNonNull(leveling, "leveling");
    }

    public RepositorySnapshots(RewardSnapshot rewards, SpawningSnapshot spawning, BossSnapshot bosses) {
        this(rewards, spawning, bosses, LevelingSnapshot.empty());
    }

    public static RepositorySnapshots empty() {
        return new RepositorySnapshots(RewardSnapshot.empty(), SpawningSnapshot.empty(), BossSnapshot.empty());
    }

    public RepositorySnapshots withRewards(RewardSnapshot value) {
        return new RepositorySnapshots(value, spawning, bosses, leveling);
    }

    public RepositorySnapshots withSpawning(SpawningSnapshot value) {
        return new RepositorySnapshots(rewards, value, bosses, leveling);
    }

    public RepositorySnapshots withBosses(BossSnapshot value) {
        return new RepositorySnapshots(rewards, spawning, value, leveling);
    }
}
