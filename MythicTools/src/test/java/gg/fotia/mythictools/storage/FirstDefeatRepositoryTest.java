package gg.fotia.mythictools.storage;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.fotia.mythictools.reward.FirstDefeatScope;
import gg.fotia.mythictools.reward.FirstDefeatSource;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirstDefeatRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsPlayerClaimsIndependentlyAndPersistsThem() throws Exception {
        Path database = tempDir.resolve("player-claims.db");
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        FirstDefeatSource source = FirstDefeatSource.boss("forest-king");

        try (Fixture fixture = new Fixture(database)) {
            assertTrue(fixture.claim(FirstDefeatScope.PLAYER, firstPlayer, source));
            assertFalse(fixture.claim(FirstDefeatScope.PLAYER, firstPlayer, source));
            assertTrue(fixture.claim(FirstDefeatScope.PLAYER, secondPlayer, source));
        }

        try (Fixture reopened = new Fixture(database)) {
            assertFalse(reopened.claim(FirstDefeatScope.PLAYER, firstPlayer, source));
            assertFalse(reopened.claim(FirstDefeatScope.PLAYER, secondPlayer, source));
        }
    }

    @Test
    void allowsOnlyOneServerClaimRegardlessOfPlayer() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("server-claim.db"))) {
            FirstDefeatSource source = FirstDefeatSource.mob("SkeletalMinion");

            assertTrue(fixture.claim(FirstDefeatScope.SERVER, UUID.randomUUID(), source));
            assertFalse(fixture.claim(FirstDefeatScope.SERVER, UUID.randomUUID(), source));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final FirstDefeatRepository repository;

        private Fixture(Path database) throws Exception {
            repository = new FirstDefeatRepository(database.toFile(), executor);
        }

        private boolean claim(FirstDefeatScope scope, UUID playerId, FirstDefeatSource source)
                throws Exception {
            return repository.tryClaim(scope, playerId, source).toCompletableFuture().get(5, SECONDS);
        }

        @Override
        public void close() {
            repository.close();
        }
    }
}
