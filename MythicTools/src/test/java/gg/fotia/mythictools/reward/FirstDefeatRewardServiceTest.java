package gg.fotia.mythictools.reward;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class FirstDefeatRewardServiceTest {
    @Test
    void grantsEachPlayerOnceForPlayerScope() throws Exception {
        Set<String> claims = new HashSet<>();
        List<UUID> delivered = new ArrayList<>();
        FirstDefeatRewardService service = new FirstDefeatRewardService(
                (scope, playerId, source) -> CompletableFuture.completedFuture(
                        claims.add(scope + ":" + playerId + ":" + source.type() + ":" + source.id())),
                ignored -> List.of(grant()),
                (recipient, grants, location, variables, forceInventory) ->
                        delivered.add(recipient.playerId()),
                Runnable::run);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        FirstDefeatRewardConfig config = config(FirstDefeatScope.PLAYER);
        List<RewardRecipient> recipients = List.of(
                new RewardRecipient(first, "First", null),
                new RewardRecipient(second, "Second", null));

        service.award(FirstDefeatSource.boss("forest-king"), config, recipients,
                null, Map.of(), true).toCompletableFuture().get(5, SECONDS);
        service.award(FirstDefeatSource.boss("forest-king"), config, recipients,
                null, Map.of(), true).toCompletableFuture().get(5, SECONDS);

        assertEquals(List.of(first, second), delivered);
    }

    @Test
    void grantsServerScopeOnlyOnce() throws Exception {
        Set<String> claims = new HashSet<>();
        List<UUID> delivered = new ArrayList<>();
        FirstDefeatRewardService service = new FirstDefeatRewardService(
                (scope, playerId, source) -> CompletableFuture.completedFuture(
                        claims.add(scope + ":" + source.type() + ":" + source.id())),
                ignored -> List.of(grant()),
                (recipient, grants, location, variables, forceInventory) ->
                        delivered.add(recipient.playerId()),
                Runnable::run);
        UUID killer = UUID.randomUUID();
        FirstDefeatRewardConfig config = config(FirstDefeatScope.SERVER);

        service.award(FirstDefeatSource.mob("SkeletalMinion"), config,
                List.of(new RewardRecipient(killer, "Killer", null)), null, Map.of(), false)
                .toCompletableFuture().get(5, SECONDS);
        service.award(FirstDefeatSource.mob("SkeletalMinion"), config,
                List.of(new RewardRecipient(UUID.randomUUID(), "Other", null)), null, Map.of(), false)
                .toCompletableFuture().get(5, SECONDS);

        assertEquals(List.of(killer), delivered);
    }

    private static FirstDefeatRewardConfig config(FirstDefeatScope scope) {
        return new FirstDefeatRewardConfig(
                true, scope, FirstDefeatRecipient.KILLER,
                List.of(new RewardEntryRef("first", "trophy")));
    }

    private static RewardGrant grant() {
        RewardEntry entry = new RewardEntry(
                "trophy", RewardType.COMMAND, RewardGrantMode.FIRST_DEFEAT, 0L,
                1, 1, "common", Map.of("zh_CN", "Trophy"), Map.of(), null, null,
                "say trophy", CommandExecutorType.CONSOLE);
        return new RewardGrant(entry, 1);
    }
}
