package gg.fotia.mythictools.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class BossPointScheduleTest {
    private static final Instant JANUARY_5 = Instant.parse("2026-01-05T00:00:00Z");

    @Test
    void prioritizesTheNextCustomWindowSlotOverTheFallbackInterval() {
        BossPointSchedule schedule = new BossPointSchedule(
                7200L,
                ZoneOffset.UTC,
                List.of(new BossTimeWindow("evening", LocalTime.of(18, 0), LocalTime.of(21, 0), 1800L)));

        assertEquals(Instant.parse("2026-01-05T18:00:00Z"),
                schedule.initialNext(Instant.parse("2026-01-05T17:00:00Z")));
        assertEquals(Instant.parse("2026-01-05T18:30:00Z"),
                schedule.initialNext(Instant.parse("2026-01-05T18:10:00Z")));
    }

    @Test
    void usesTheFallbackIntervalWhenNoTimeWindowIsConfigured() {
        BossPointSchedule schedule = new BossPointSchedule(7200L, ZoneOffset.UTC, List.of());

        assertEquals(JANUARY_5.plusSeconds(7200L), schedule.initialNext(JANUARY_5));
        assertEquals(JANUARY_5.plusSeconds(7200L), schedule.nextAfter(JANUARY_5));
    }

    @Test
    void supportsCustomWindowNamesAndCrossMidnightWindows() {
        BossPointSchedule schedule = new BossPointSchedule(
                7200L,
                ZoneOffset.UTC,
                List.of(
                        new BossTimeWindow("morning", LocalTime.of(8, 0), LocalTime.of(10, 0), 1800L),
                        new BossTimeWindow("noon", LocalTime.of(12, 0), LocalTime.of(14, 0), 900L),
                        new BossTimeWindow("night", LocalTime.of(22, 0), LocalTime.of(2, 0), 3600L)));

        assertEquals(Instant.parse("2026-01-05T12:15:00Z"),
                schedule.initialNext(Instant.parse("2026-01-05T12:03:00Z")));
        assertEquals(Instant.parse("2026-01-06T01:00:00Z"),
                schedule.initialNext(Instant.parse("2026-01-06T00:10:00Z")));
    }

    @Test
    void rejectsOverlappingTimeWindows() {
        assertThrows(IllegalArgumentException.class, () -> new BossPointSchedule(
                7200L,
                ZoneOffset.UTC,
                List.of(
                        new BossTimeWindow("morning", LocalTime.of(8, 0), LocalTime.of(10, 0), 1800L),
                        new BossTimeWindow("noon", LocalTime.of(9, 30), LocalTime.of(12, 0), 1800L))));
    }
}
