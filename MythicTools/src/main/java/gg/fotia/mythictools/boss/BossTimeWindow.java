package gg.fotia.mythictools.boss;

import java.time.LocalTime;
import java.util.Objects;

/** 固定 Boss 点在一天内使用独立刷新间隔的命名时间段。 */
public record BossTimeWindow(
        String id,
        LocalTime start,
        LocalTime end,
        long intervalSeconds) {

    public BossTimeWindow {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("时间段 ID 不能为空");
        }
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.equals(end)) {
            throw new IllegalArgumentException("时间段开始和结束时间不能相同");
        }
        if (intervalSeconds < 1L) {
            throw new IllegalArgumentException("时间段刷新间隔必须至少为 1 秒");
        }
    }
}
