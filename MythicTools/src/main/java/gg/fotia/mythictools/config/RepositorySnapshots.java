package gg.fotia.mythictools.config;

import gg.fotia.mythictools.boss.BossSnapshot;
import gg.fotia.mythictools.reward.RewardSnapshot;
import gg.fotia.mythictools.spawning.SpawningSnapshot;
import java.util.Objects;

/** 三个配置域在同一发布时刻对应的快照束。 */
public record RepositorySnapshots(
        RewardSnapshot rewards,
        SpawningSnapshot spawning,
        BossSnapshot bosses) {

    public RepositorySnapshots {
        Objects.requireNonNull(rewards, "rewards");
        Objects.requireNonNull(spawning, "spawning");
        Objects.requireNonNull(bosses, "bosses");
    }

    public static RepositorySnapshots empty() {
        return new RepositorySnapshots(RewardSnapshot.empty(), SpawningSnapshot.empty(), BossSnapshot.empty());
    }

    public RepositorySnapshots withRewards(RewardSnapshot value) {
        return new RepositorySnapshots(value, spawning, bosses);
    }

    public RepositorySnapshots withSpawning(SpawningSnapshot value) {
        return new RepositorySnapshots(rewards, value, bosses);
    }

    public RepositorySnapshots withBosses(BossSnapshot value) {
        return new RepositorySnapshots(rewards, spawning, value);
    }
}
