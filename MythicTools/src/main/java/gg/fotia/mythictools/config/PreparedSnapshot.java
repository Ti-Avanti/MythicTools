package gg.fotia.mythictools.config;

import java.util.Objects;

/** 尚未发布的不可变候选快照及其诊断。 */
public record PreparedSnapshot<T>(T snapshot, ConfigLoadReport report) {
    public PreparedSnapshot {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(report, "report");
    }
}
