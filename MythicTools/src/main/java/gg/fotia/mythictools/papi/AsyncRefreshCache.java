package gg.fotia.mythictools.papi;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** 异步读取返回最近结果；过期后合并主线程刷新，重载后拒绝旧回调写回。 */
final class AsyncRefreshCache<K, V> {
    private final int limit;
    private final LongSupplier clock;
    private final Map<K, Cached<V>> values = new LinkedHashMap<>(128, 0.75f, true);
    private final Map<K, Object> pending = new HashMap<>();
    private long refreshNanos;

    AsyncRefreshCache(int limit) {
        this(limit, 250L, System::nanoTime);
    }

    AsyncRefreshCache(int limit, long refreshMillis, LongSupplier clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("缓存上限必须大于 0");
        }
        this.limit = limit;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        configure(refreshMillis);
    }

    synchronized void configure(long refreshMillis) {
        if (refreshMillis < 1L) {
            throw new IllegalArgumentException("刷新间隔必须大于 0");
        }
        refreshNanos = TimeUnit.MILLISECONDS.toNanos(refreshMillis);
        clear();
    }

    V getOrSchedule(K key, Consumer<Runnable> scheduler, Supplier<V> loader) {
        Cached<V> cached;
        Object token;
        synchronized (this) {
            cached = values.get(key);
            if (cached != null && clock.getAsLong() - cached.updatedAt() < refreshNanos) {
                return cached.value();
            }
            if (pending.containsKey(key) || pending.size() >= limit) {
                return cached == null ? null : cached.value();
            }
            token = new Object();
            pending.put(key, token);
        }
        try {
            scheduler.accept(() -> {
                try {
                    V loaded = loader.get();
                    synchronized (this) {
                        if (pending.get(key) == token && loaded != null) {
                            put(key, loaded);
                        }
                    }
                } finally {
                    synchronized (this) {
                        pending.remove(key, token);
                    }
                }
            });
        } catch (RuntimeException ignored) {
            synchronized (this) {
                pending.remove(key, token);
            }
        }
        return cached == null ? null : cached.value();
    }

    synchronized void put(K key, V value) {
        values.put(key, new Cached<>(value, clock.getAsLong()));
        if (values.size() > limit) {
            values.remove(values.keySet().iterator().next());
        }
    }

    synchronized void clear() {
        values.clear();
        pending.clear();
    }

    private record Cached<V>(V value, long updatedAt) {
    }
}
