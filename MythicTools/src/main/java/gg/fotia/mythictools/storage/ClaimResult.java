package gg.fotia.mythictools.storage;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** 一次领取请求的接纳状态及当前领取完成信号。 */
public record ClaimResult(Status status, CompletionStage<Void> completion) {
    public ClaimResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(completion, "completion");
    }

    public enum Status {
        ACCEPTED,
        BUSY,
        CLOSING
    }
}
