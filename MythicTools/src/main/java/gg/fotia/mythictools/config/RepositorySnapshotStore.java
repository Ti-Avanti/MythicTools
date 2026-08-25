package gg.fotia.mythictools.config;

import java.util.Objects;

/** 全部仓库唯一的原子发布指针。 */
public final class RepositorySnapshotStore {
    private volatile RepositorySnapshots current;

    public RepositorySnapshotStore() {
        this(RepositorySnapshots.empty());
    }

    public RepositorySnapshotStore(RepositorySnapshots initial) {
        current = Objects.requireNonNull(initial, "initial");
    }

    public RepositorySnapshots current() {
        return current;
    }

    public void publish(RepositorySnapshots snapshots) {
        current = Objects.requireNonNull(snapshots, "snapshots");
    }
}
