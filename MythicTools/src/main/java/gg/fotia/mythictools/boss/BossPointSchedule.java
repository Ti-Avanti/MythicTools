package gg.fotia.mythictools.boss;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 计算固定 Boss 点的下一次刷新时间；窗口以各自开始时间作为间隔对齐基准。 */
public record BossPointSchedule(
        long fallbackIntervalSeconds,
        ZoneId zoneId,
        List<BossTimeWindow> timeWindows) {

    public BossPointSchedule {
        if (fallbackIntervalSeconds < 1L) {
            throw new IllegalArgumentException("默认刷新间隔必须至少为 1 秒");
        }
        Objects.requireNonNull(zoneId, "zoneId");
        timeWindows = List.copyOf(timeWindows);
        validateTimeWindows(timeWindows);
    }

    public static BossPointSchedule fixedInterval(long intervalSeconds) {
        return new BossPointSchedule(intervalSeconds, ZoneId.systemDefault(), List.of());
    }

    /** 返回启动或重载后第一个有效刷新时间；时间窗口优先于默认间隔。 */
    public Instant initialNext(Instant now) {
        return next(now, false);
    }

    /** 返回一次计划时间被消费后的下一个刷新时间。 */
    public Instant nextAfter(Instant now) {
        return next(now, true);
    }

    private Instant next(Instant now, boolean strictlyAfter) {
        Instant fallback = now.plusSeconds(fallbackIntervalSeconds);
        return timeWindows.stream()
                .map(window -> nextWindowSlot(window, now, strictlyAfter))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .filter(candidate -> candidate.isBefore(fallback))
                .orElse(fallback);
    }

    private Instant nextWindowSlot(BossTimeWindow window, Instant now, boolean strictlyAfter) {
        LocalDate currentDate = now.atZone(zoneId).toLocalDate();
        Instant result = null;
        for (int dayOffset = -1; dayOffset <= 1; dayOffset++) {
            Instant candidate = slotInOccurrence(window, currentDate.plusDays(dayOffset), now, strictlyAfter);
            if (candidate != null && (result == null || candidate.isBefore(result))) {
                result = candidate;
            }
        }
        return result;
    }

    private Instant slotInOccurrence(
            BossTimeWindow window,
            LocalDate startDate,
            Instant now,
            boolean strictlyAfter) {
        ZonedDateTime start = ZonedDateTime.of(startDate, window.start(), zoneId);
        ZonedDateTime end = ZonedDateTime.of(startDate, window.end(), zoneId);
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        Instant startInstant = start.toInstant();
        Instant endInstant = end.toInstant();
        if (!now.isBefore(endInstant)) {
            return null;
        }
        if (now.isBefore(startInstant)) {
            return startInstant;
        }
        long elapsedSeconds = Math.max(0L, Duration.between(startInstant, now).getSeconds());
        long slotIndex = elapsedSeconds / window.intervalSeconds();
        Instant candidate = startInstant.plusSeconds(slotIndex * window.intervalSeconds());
        if (candidate.isBefore(now) || strictlyAfter) {
            candidate = candidate.plusSeconds(window.intervalSeconds());
        }
        return candidate.isBefore(endInstant) ? candidate : null;
    }

    private static void validateTimeWindows(List<BossTimeWindow> windows) {
        Set<String> ids = new HashSet<>();
        for (BossTimeWindow window : windows) {
            if (!ids.add(window.id())) {
                throw new IllegalArgumentException("时间段 ID 重复: " + window.id());
            }
        }
        for (int first = 0; first < windows.size(); first++) {
            for (int second = first + 1; second < windows.size(); second++) {
                if (overlaps(windows.get(first), windows.get(second))) {
                    throw new IllegalArgumentException("时间段重叠: "
                            + windows.get(first).id() + " 与 " + windows.get(second).id());
                }
            }
        }
    }

    private static boolean overlaps(BossTimeWindow first, BossTimeWindow second) {
        for (TimeRange firstRange : ranges(first)) {
            for (TimeRange secondRange : ranges(second)) {
                if (firstRange.start < secondRange.end && secondRange.start < firstRange.end) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<TimeRange> ranges(BossTimeWindow window) {
        int start = window.start().toSecondOfDay();
        int end = window.end().toSecondOfDay();
        return end > start ? List.of(new TimeRange(start, end))
                : List.of(new TimeRange(start, 86_400), new TimeRange(0, end));
    }

    private record TimeRange(int start, int end) {
    }
}
