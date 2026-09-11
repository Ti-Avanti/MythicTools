package gg.fotia.mythictools.spawning;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** 主线程按 tick 预算轮转生成批次，同一来源在队列中最多保留一批。 */
final class SpawnBatchQueue {
    private final int limit;
    private final ArrayDeque<Batch> queue = new ArrayDeque<>();
    private final Set<String> sources = new HashSet<>();
    private int remaining;
    private boolean draining;
    private long generation;

    SpawnBatchQueue(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("每 tick 生成预算必须大于 0");
        }
        this.limit = limit;
        remaining = limit;
    }

    void tick() {
        remaining = limit;
        drain();
    }

    boolean contains(String source) {
        return sources.contains(source);
    }

    void submit(String source, int amount, BooleanSupplier spawn) {
        if (amount <= 0 || !sources.add(source)) {
            return;
        }
        queue.addLast(new Batch(source, amount, spawn));
        drain();
    }

    private void drain() {
        if (draining) {
            return;
        }
        draining = true;
        try {
            while (remaining > 0 && !queue.isEmpty()) {
                Batch batch = queue.removeFirst();
                remaining--;
                long currentGeneration = generation;
                boolean spawned;
                try {
                    spawned = batch.spawn.getAsBoolean();
                } catch (RuntimeException exception) {
                    sources.remove(batch.source);
                    throw exception;
                }
                if (generation != currentGeneration) {
                    return;
                }
                if (spawned && --batch.remaining > 0) {
                    queue.addLast(batch);
                } else {
                    sources.remove(batch.source);
                }
            }
        } finally {
            draining = false;
        }
    }

    void clear() {
        generation++;
        queue.clear();
        sources.clear();
        remaining = limit;
    }

    private static final class Batch {
        private final String source;
        private final BooleanSupplier spawn;
        private int remaining;

        private Batch(String source, int remaining, BooleanSupplier spawn) {
            this.source = source;
            this.remaining = remaining;
            this.spawn = spawn;
        }
    }
}
