package gg.fotia.mythictools.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.bukkit.Location;

/** 异步争抢首次击败记录，并在主线程交付确定奖励。 */
public final class FirstDefeatRewardService {
    private final ClaimStore claims;
    private final Function<List<RewardEntryRef>, List<RewardGrant>> selector;
    private final Delivery delivery;
    private final Executor mainThread;
    private final FirstDefeatDispatcher dispatcher;

    public FirstDefeatRewardService(
            ClaimStore claims,
            Function<List<RewardEntryRef>, List<RewardGrant>> selector,
            Delivery delivery,
            Executor mainThread) {
        this.claims = claims;
        this.selector = selector;
        this.delivery = delivery;
        this.mainThread = mainThread;
        this.dispatcher = null;
    }

    public FirstDefeatRewardService(
            Function<List<RewardEntryRef>, List<RewardGrant>> selector, FirstDefeatDispatcher dispatcher) {
        this.claims = null;
        this.selector = selector;
        this.delivery = null;
        this.mainThread = null;
        this.dispatcher = java.util.Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /** 返回所有领取判定与主线程交付完成后的阶段，调用方无需阻塞等待。 */
    public CompletionStage<Void> award(
            FirstDefeatSource source,
            FirstDefeatRewardConfig config,
            List<RewardRecipient> recipients,
            Location location,
            Map<String, ?> variables,
            boolean forceInventory) {
        if (!config.enabled() || recipients.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> completions = new ArrayList<>();
        for (RewardRecipient recipient : recipients) {
            if (dispatcher != null) {
                try {
                    completions.add(dispatcher.enqueue(config.scope(), source, recipient,
                            () -> selector.apply(config.entries()), location, variables, forceInventory).toCompletableFuture());
                } catch (RuntimeException exception) {
                    completions.add(CompletableFuture.failedFuture(exception));
                }
                if (config.scope() == FirstDefeatScope.SERVER) {
                    break;
                }
                continue;
            }
            CompletableFuture<Void> completion = new CompletableFuture<>();
            completions.add(completion);
            CompletionStage<Boolean> claim;
            try {
                claim = claims.tryClaim(config.scope(), recipient.playerId(), source);
            } catch (RuntimeException exception) {
                completion.completeExceptionally(exception);
                continue;
            }
            claim.whenComplete((accepted, failure) -> {
                if (failure != null) {
                    completion.completeExceptionally(failure);
                    return;
                }
                if (!Boolean.TRUE.equals(accepted)) {
                    completion.complete(null);
                    return;
                }
                try {
                    mainThread.execute(() -> deliver(
                            recipient, config, location, variables, forceInventory, completion));
                } catch (RuntimeException exception) {
                    completion.completeExceptionally(exception);
                }
            });
            if (config.scope() == FirstDefeatScope.SERVER) {
                break;
            }
        }
        return CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
    }

    private void deliver(
            RewardRecipient recipient,
            FirstDefeatRewardConfig config,
            Location location,
            Map<String, ?> variables,
            boolean forceInventory,
            CompletableFuture<Void> completion) {
        try {
            Map<String, Object> resolvedVariables = new HashMap<>(variables);
            resolvedVariables.put("player", recipient.playerName());
            delivery.deliver(
                    recipient, selector.apply(config.entries()), location,
                    Map.copyOf(resolvedVariables), forceInventory);
            completion.complete(null);
        } catch (RuntimeException exception) {
            completion.completeExceptionally(exception);
        }
    }

    @FunctionalInterface
    public interface ClaimStore {
        CompletionStage<Boolean> tryClaim(
                FirstDefeatScope scope,
                UUID playerId,
                FirstDefeatSource source);
    }

    @FunctionalInterface
    public interface Delivery {
        void deliver(
                RewardRecipient recipient,
                List<RewardGrant> grants,
                Location location,
                Map<String, ?> variables,
                boolean forceInventory);
    }
}
