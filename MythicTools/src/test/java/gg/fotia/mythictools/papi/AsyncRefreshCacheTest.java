package gg.fotia.mythictools.papi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncRefreshCacheTest {
    @Test
    void coldMissSchedulesOneRefreshAndLaterReturnsItsValue() {
        AsyncRefreshCache<String, String> cache = new AsyncRefreshCache<>(4);
        List<Runnable> scheduled = new ArrayList<>();
        AtomicInteger loads = new AtomicInteger();

        assertNull(cache.getOrSchedule("language", scheduled::add, () -> {
            loads.incrementAndGet();
            return "zh_CN";
        }));
        assertNull(cache.getOrSchedule("language", scheduled::add, () -> {
            loads.incrementAndGet();
            return "duplicate";
        }));
        assertEquals(1, scheduled.size());

        scheduled.get(0).run();

        assertEquals(1, loads.get());
        assertEquals("zh_CN", cache.getOrSchedule("language", scheduled::add, () -> "unused"));
        assertEquals(1, scheduled.size());
    }

    @Test
    void failedSchedulingDoesNotLeaveKeyPermanentlyPending() {
        AsyncRefreshCache<String, String> cache = new AsyncRefreshCache<>(4);
        AtomicInteger schedules = new AtomicInteger();

        assertNull(cache.getOrSchedule("language", ignored -> {
            schedules.incrementAndGet();
            throw new IllegalStateException("scheduler stopped");
        }, () -> "unused"));
        assertNull(cache.getOrSchedule("language", ignored -> schedules.incrementAndGet(), () -> "unused"));

        assertEquals(2, schedules.get());
    }
}
