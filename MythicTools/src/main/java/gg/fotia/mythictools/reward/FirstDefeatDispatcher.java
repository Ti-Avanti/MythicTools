package gg.fotia.mythictools.reward;

import gg.fotia.mythictools.storage.FirstDefeatRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;

/**
 * 跨运行时的首次奖励交付器；任务取消、掉线或重启后由数据库待发记录接续。
 * 与背包变更、外部命令之间采用至少一次交付语义；进程在交付后、确认前崩溃可能重放。
 */
public final class FirstDefeatDispatcher implements AutoCloseable {
    private final FirstDefeatRepository store;
    private final Supplier<RewardService> rewards;
    private final Executor mainThread;
    private final Logger logger;
    private final Map<UUID, CompletableFuture<Void>> active = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> acknowledgements = new ConcurrentHashMap<>();
    private final Set<UUID> acknowledging = ConcurrentHashMap.newKeySet();
    private final Set<ClaimKey> admitting = ConcurrentHashMap.newKeySet();
    private final Map<ClaimKey, Boolean> knownClaims = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ClaimKey, Boolean> eldest) {
                    return size() > 8192;
                }
            });
    private final AtomicBoolean scanning = new AtomicBoolean();
    private volatile boolean closed;

    public FirstDefeatDispatcher(FirstDefeatRepository store, Supplier<RewardService> rewards,
                                 Executor mainThread, Logger logger) {
        this.store = store;
        this.rewards = rewards;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    public CompletionStage<Void> enqueue(FirstDefeatScope scope, FirstDefeatSource source,
            RewardRecipient recipient, Supplier<List<RewardGrant>> grants, Location location,
            Map<String, ?> variables, boolean forceInventory) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("首次奖励交付器已关闭"));
        }
        ClaimKey key = new ClaimKey(scope, scope == FirstDefeatScope.SERVER ? null : recipient.playerId(), source);
        if (knownClaims.get(key) != null || !admitting.add(key)) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            UUID id = UUID.randomUUID();
            String payload = FirstDefeatPayload.encode(recipient, grants.get(), location, variables, forceInventory);
            return store.enqueue(scope, recipient.playerId(), source, id, payload)
                    .whenComplete((accepted, failure) -> {
                        if (failure == null) {
                            knownClaims.put(key, Boolean.TRUE);
                        }
                        admitting.remove(key);
                    })
                    .thenCompose(accepted -> accepted ? dispatch(id, payload) : CompletableFuture.completedFuture(null));
        } catch (RuntimeException exception) {
            admitting.remove(key);
            throw exception;
        }
    }

    /** 有界扫描，主线程只接收已读取的奖励快照。 */
    public void recover() {
        if (closed || !scanning.compareAndSet(false, true)) {
            return;
        }
        try {
            acknowledgements.forEach(this::acknowledge);
            store.pendingRewards().whenComplete((pending, failure) -> {
                try {
                    if (failure != null) {
                        logger.log(Level.WARNING, "读取首次击败待发奖励失败", failure);
                    } else if (!closed) {
                        pending.forEach(reward -> dispatch(reward.id(), reward.payload()));
                    }
                } finally {
                    scanning.set(false);
                }
            });
        } catch (RuntimeException exception) {
            scanning.set(false);
            if (!closed) {
                logger.log(Level.WARNING, "调度首次击败奖励恢复失败", exception);
            }
        }
    }

    private CompletionStage<Void> dispatch(UUID id, String payload) {
        if (closed || active.size() >= 128) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        CompletableFuture<Void> existing = active.putIfAbsent(id, completion);
        if (existing != null) {
            return existing;
        }
        completion.whenComplete((ignored, failure) -> {
            active.remove(id, completion);
            if (failure != null && !closed) {
                logger.log(Level.WARNING, "首次击败奖励尚未完成，待发记录已保留: " + id, failure);
            }
        });
        if (closed) {
            completion.complete(null);
            return completion;
        }
        try {
            mainThread.execute(() -> {
                if (closed) {
                    completion.complete(null);
                    return;
                }
                try {
                    FirstDefeatPayload.Restored restored = FirstDefeatPayload.decode(payload);
                    if (!restored.ready()) {
                        retry(id, completion, null);
                        return;
                    }
                    RewardRecipient recipient = restored.recipient();
                    rewards.get().deliverTracked(recipient.playerId(), recipient.playerName(), recipient.onlinePlayer(),
                            restored.grants(), restored.location(), restored.variables(), restored.forceInventory())
                            .whenComplete((ignored, failure) -> {
                                if (failure != null) {
                                    retry(id, completion, failure);
                                } else {
                                    acknowledge(id, completion);
                                }
                            });
                } catch (Exception exception) {
                    retry(id, completion, exception);
                }
            });
        } catch (RuntimeException exception) {
            complete(completion, exception);
        }
        return completion;
    }

    /** 发奖成功后只重试数据库确认，避免短暂锁库引起同一次运行内的重复发奖。 */
    private void acknowledge(UUID id, CompletableFuture<Void> completion) {
        if (closed) {
            completion.complete(null);
            return;
        }
        acknowledgements.put(id, completion);
        if (!acknowledging.add(id)) {
            return;
        }
        try {
            store.completeReward(id).whenComplete((ignored, failure) -> {
                acknowledging.remove(id);
                if (failure == null) {
                    acknowledgements.remove(id, completion);
                    completion.complete(null);
                } else if (!closed) {
                    logger.log(Level.WARNING, "首次击败奖励已交付，稍后重试确认记录: " + id, failure);
                }
            });
        } catch (RuntimeException exception) {
            acknowledging.remove(id);
            if (!closed) {
                logger.log(Level.WARNING, "无法调度首次击败奖励确认: " + id, exception);
            }
        }
    }

    private void retry(UUID id, CompletableFuture<Void> completion, Throwable failure) {
        try {
            store.retryReward(id, System.currentTimeMillis() + 5000L).whenComplete((ignored, retryFailure) ->
                    complete(completion, failure == null ? retryFailure : failure));
        } catch (RuntimeException exception) {
            complete(completion, exception);
        }
    }

    private static void complete(CompletableFuture<Void> completion, Throwable failure) {
        if (failure == null) {
            completion.complete(null);
        } else {
            completion.completeExceptionally(failure);
        }
    }

    @Override
    public void close() {
        closed = true;
        active.values().forEach(completion -> completion.complete(null));
        active.clear();
        acknowledgements.clear();
        acknowledging.clear();
        admitting.clear();
        knownClaims.clear();
    }

    private record ClaimKey(FirstDefeatScope scope, UUID playerId, FirstDefeatSource source) {
    }
}
