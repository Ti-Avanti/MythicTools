package gg.fotia.mythictools.papi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 为异步占位符读取合并主线程刷新请求的有界缓存。 */
final class AsyncRefreshCache<K, V> {
    private final Map<K, V> values;
    private final Set<K> pending = ConcurrentHashMap.newKeySet();

    AsyncRefreshCache(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("缓存上限必须大于 0");
        }
        values = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > limit;
            }
        });
    }

    V getOrSchedule(K key, Consumer<Runnable> scheduler, Supplier<V> loader) {
        V cached = values.get(key);
        if (cached != null) {
            return cached;
        }
        if (pending.add(key)) {
            try {
                scheduler.accept(() -> {
                    try {
                        V loaded = loader.get();
                        if (loaded != null) {
                            values.put(key, loaded);
                        }
                    } finally {
                        pending.remove(key);
                    }
                });
            } catch (RuntimeException ignored) {
                pending.remove(key);
            }
        }
        return null;
    }

    void put(K key, V value) {
        values.put(key, value);
    }

    void clear() {
        values.clear();
        pending.clear();
    }
}
