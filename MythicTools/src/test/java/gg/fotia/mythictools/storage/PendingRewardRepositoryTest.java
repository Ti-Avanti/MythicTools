package gg.fotia.mythictools.storage;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingRewardRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void keepsOriginalRowsVisibleUntilDeliveryReturnsThenDeletesThem() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("visible-until-delivered.db"))) {
            UUID playerId = UUID.randomUUID();
            assertEquals(QueueResult.STORED, await(fixture.repository.queue(
                    playerId, List.of(item(Material.STONE, 2)))));

            ClaimResult claim = fixture.repository.claim(playerId, items -> {
                assertEquals(1, fixture.countRows(playerId));
                assertEquals(2, items.get(0).getAmount());
                return List.of();
            });
            fixture.awaitDatabase();

            assertEquals(ClaimResult.Status.ACCEPTED, claim.status());
            assertEquals(1, fixture.countRows(playerId));
            fixture.mainThread.runNext();
            await(claim.completion());

            assertEquals(0, fixture.countRows(playerId));
        }
    }

    @Test
    void preservesOriginalRowsWhenDeliveryThrows() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("delivery-failure.db"))) {
            UUID playerId = UUID.randomUUID();
            assertEquals(QueueResult.STORED, await(fixture.repository.queue(
                    playerId, List.of(item(Material.DIAMOND, 1)))));

            ClaimResult claim = fixture.repository.claim(playerId, items -> {
                throw new IllegalStateException("delivery failed");
            });
            fixture.awaitDatabase();
            fixture.mainThread.runNext();
            await(claim.completion());

            assertEquals(1, fixture.countRows(playerId));
        }
    }

    @Test
    void isolatesCorruptRowsAndStillDeliversValidRows() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("corrupt-item.db"))) {
            UUID playerId = UUID.randomUUID();
            assertEquals(QueueResult.STORED, await(fixture.repository.queue(
                    playerId, List.of(item(Material.STONE, 1)))));
            fixture.insertRaw(playerId, new byte[] {0x01, 0x02, 0x03});
            AtomicBoolean delivered = new AtomicBoolean();

            ClaimResult claim = fixture.repository.claim(playerId, items -> {
                delivered.set(true);
                assertEquals(1, items.size());
                assertEquals(Material.STONE, items.get(0).getType());
                return List.of();
            });
            fixture.awaitDatabase();

            assertEquals(1, fixture.countRows(playerId));
            assertEquals(1, fixture.countQuarantinedRows(playerId));
            fixture.mainThread.runNext();
            await(claim.completion());

            assertTrue(delivered.get());
            assertEquals(0, fixture.countRows(playerId));
            assertEquals(1, fixture.countQuarantinedRows(playerId));
        }
    }

    @Test
    void atomicallyReplacesClaimedRowsWithReturnedLeftovers() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("leftovers.db"))) {
            UUID playerId = UUID.randomUUID();
            assertEquals(QueueResult.STORED, await(fixture.repository.queue(playerId, List.of(
                    item(Material.STONE, 4),
                    item(Material.IRON_INGOT, 3)))));
            fixture.awaitDatabase();
            List<Long> originalIds = fixture.rowIds(playerId);

            ClaimResult claim = fixture.repository.claim(
                    playerId, items -> List.of(item(Material.DIAMOND, 2)));
            fixture.awaitDatabase();
            fixture.mainThread.runNext();
            await(claim.completion());

            List<StoredRow> rows = fixture.rows(playerId);
            assertEquals(1, rows.size());
            assertNotEquals(originalIds.get(0), rows.get(0).id());
            assertNotEquals(originalIds.get(1), rows.get(0).id());
            assertEquals(Material.DIAMOND, rows.get(0).item().getType());
            assertEquals(2, rows.get(0).item().getAmount());
        }
    }

    @Test
    void snapshotsMutableLeftoversBeforeDatabaseHandoff() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("mutable-leftover.db"))) {
            UUID playerId = UUID.randomUUID();
            ItemStack leftover = item(Material.EMERALD, 2);
            assertEquals(QueueResult.STORED, await(fixture.repository.queue(
                    playerId, List.of(item(Material.STONE, 1)))));
            ClaimResult claim = fixture.repository.claim(playerId, items -> List.of(leftover));
            fixture.awaitDatabase();

            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            fixture.database.execute(() -> {
                blockerStarted.countDown();
                try {
                    releaseBlocker.await(5, SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(blockerStarted.await(5, SECONDS));

            fixture.mainThread.runNext();
            leftover.setAmount(9);
            releaseBlocker.countDown();
            await(claim.completion());

            assertEquals(2, fixture.rows(playerId).get(0).item().getAmount());
        }
    }

    @Test
    void allowsOnlyOneClaimPerPlayerAndExposesCompletionToBusyCaller() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("single-claim.db"))) {
            UUID playerId = UUID.randomUUID();
            assertEquals(QueueResult.STORED, await(fixture.repository.queue(
                    playerId, List.of(item(Material.GOLD_INGOT, 1)))));

            ClaimResult first = fixture.repository.claim(playerId, items -> List.of());
            ClaimResult second = fixture.repository.claim(playerId, items -> List.of());
            fixture.awaitDatabase();

            assertEquals(ClaimResult.Status.ACCEPTED, first.status());
            assertEquals(ClaimResult.Status.BUSY, second.status());
            assertSame(first.completion(), second.completion());
            assertEquals(1, fixture.mainThread.size());
            fixture.mainThread.runNext();
            await(first.completion());
            assertEquals(0, fixture.countRows(playerId));
        }
    }

    @Test
    void rejectsWholeBatchWhenAnyItemCannotBeSerialized() throws Exception {
        PendingRewardRepository.ItemCodec codec = new FailingItemCodec(Material.DIAMOND);
        try (Fixture fixture = new Fixture(tempDir.resolve("serialization-failure.db"), codec)) {
            UUID playerId = UUID.randomUUID();

            QueueResult result = await(fixture.repository.queue(playerId, List.of(
                    item(Material.STONE, 1),
                    item(Material.DIAMOND, 1))));

            assertEquals(QueueResult.SERIALIZATION_FAILED, result);
            assertEquals(0, fixture.countRows(playerId));
        }
    }

    @Test
    void keepsJournalOwnershipWhenSqliteBatchMigrationFails() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("transaction-failure.db"))) {
            UUID playerId = UUID.randomUUID();
            fixture.createDiamondInsertFailureTrigger();

            QueueResult result = await(fixture.repository.queue(playerId, List.of(
                    item(Material.STONE, 1),
                    item(Material.DIAMOND, 1))));
            fixture.awaitDatabase();

            assertEquals(QueueResult.STORED, result);
            assertEquals(0, fixture.countRows(playerId));
        }
    }

    @Test
    void reportsClosingWhenExecutorRejectsQueueOrRepositoryCloses() throws Exception {
        try (Fixture rejected = new Fixture(tempDir.resolve("rejected.db"))) {
            rejected.database.shutdown();
            QueueResult result = await(rejected.repository.queue(
                    UUID.randomUUID(), List.of(item(Material.STONE, 1))));
            // 执行器已停止时入队任务无法提交，日志尚未写入，必须如实报告未接管。
            assertEquals(QueueResult.CLOSING, result);
        }

        try (Fixture closing = new Fixture(tempDir.resolve("closing.db"))) {
            closing.repository.close();
            UUID playerId = UUID.randomUUID();
            assertEquals(QueueResult.CLOSING, await(closing.repository.queue(
                    playerId, List.of(item(Material.STONE, 1)))));
            assertEquals(ClaimResult.Status.CLOSING,
                    closing.repository.claim(playerId, items -> List.of()).status());
        }
    }

    @Test
    void emptyQueueAndClaimCompleteWithoutDeliveryOrBusyState() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("empty.db"))) {
            UUID playerId = UUID.randomUUID();
            assertEquals(QueueResult.STORED, await(fixture.repository.queue(playerId, List.of())));
            AtomicBoolean delivered = new AtomicBoolean();

            ClaimResult first = fixture.repository.claim(playerId, items -> {
                delivered.set(true);
                return List.of();
            });
            await(first.completion());
            ClaimResult second = fixture.repository.claim(playerId, items -> List.of());
            await(second.completion());

            assertEquals(ClaimResult.Status.ACCEPTED, first.status());
            assertEquals(ClaimResult.Status.ACCEPTED, second.status());
            assertFalse(delivered.get());
            assertEquals(0, fixture.mainThread.size());
        }
    }

    @Test
    void retainedItemsAreDeliveredByLaterClaim() throws Exception {
        try (Fixture fixture = new Fixture(tempDir.resolve("retained.db"))) {
            UUID playerId = UUID.randomUUID();
            ItemStack retained = item(Material.NETHERITE_INGOT, 1);
            assertEquals(QueueResult.STORED, await(fixture.repository.retainForRetry(
                    playerId, List.of(retained))));
            retained.setAmount(8);

            ClaimResult claim = fixture.repository.claim(playerId, items -> {
                assertEquals(1, items.size());
                assertEquals(Material.NETHERITE_INGOT, items.get(0).getType());
                return List.of();
            });
            fixture.awaitDatabase();
            fixture.mainThread.runNext();
            await(claim.completion());

            ClaimResult empty = fixture.repository.claim(playerId, items -> {
                throw new AssertionError("retained item was not cleared");
            });
            await(empty.completion());
        }
    }

    @Test
    void journalOwnsBatchBeforeAsyncSqliteMigrationStarts() throws Exception {
        Path databasePath = tempDir.resolve("journal-before-migration.db");
        Path journalPath = tempDir.resolve("journal-before-migration.bin");
        PausedExecutorService database = new PausedExecutorService();
        ManualDeliveryScheduler deliveries = new ManualDeliveryScheduler();
        PendingRewardJournal journal = new PendingRewardJournal(journalPath);
        PendingRewardRepository repository = new PendingRewardRepository(
                quietLogger("journal-before-migration"),
                databasePath.toFile(),
                database,
                deliveries,
                new TextItemCodec(),
                journal,
                Duration.ofMillis(200));
        try (Connection observer = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            UUID playerId = UUID.randomUUID();

            CompletionStage<QueueResult> queued = repository.queue(
                    playerId, List.of(item(Material.DIAMOND, 2)));
            database.runNext();
            assertEquals(QueueResult.STORED, await(queued));

            assertEquals(1, journal.batches().size());
            assertEquals(0, countRows(observer, playerId));
            database.runNext();
            assertEquals(1, countRows(observer, playerId));
            assertTrue(journal.batches().isEmpty());
        } finally {
            repository.close();
        }
    }

    @Test
    void reportsJournalAppendFailureWithoutSubmittingMigration() throws Exception {
        PausedExecutorService database = new PausedExecutorService();
        PendingRewardRepository repository = new PendingRewardRepository(
                quietLogger("journal-append-failure"),
                tempDir.resolve("journal-append-failure.db").toFile(),
                database,
                new ManualDeliveryScheduler(),
                new TextItemCodec(),
                new FailingAppendJournal(),
                Duration.ofMillis(200));
        try {
            CompletionStage<QueueResult> queued = repository.queue(
                    UUID.randomUUID(), List.of(item(Material.DIAMOND, 1)));
            assertEquals(1, database.size());
            database.runNext();
            assertEquals(QueueResult.JOURNAL_FAILED, await(queued));
            assertEquals(0, database.size());
        } finally {
            repository.close();
        }
    }

    @Test
    void startupReplayIsIdempotentAfterSqliteCommitBeforeJournalRemoval() throws Exception {
        Path databasePath = tempDir.resolve("idempotent-replay.db");
        Path journalPath = tempDir.resolve("idempotent-replay.bin");
        TextItemCodec codec = new TextItemCodec();
        try (Fixture schema = new Fixture(databasePath)) {
            // 建表即可。
        }
        UUID playerId = UUID.randomUUID();
        JournalBatch batch;
        try (PendingRewardJournal journal = new PendingRewardJournal(journalPath)) {
            batch = journal.append(playerId, List.of(codec.serialize(item(Material.EMERALD, 3))));
        }
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath());
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO pending_rewards("
                             + "player_uuid, item, created_at, batch_id, item_index) VALUES(?, ?, ?, ?, ?)")) {
            insert.setString(1, playerId.toString());
            insert.setBytes(2, batch.itemPayloads().get(0));
            insert.setLong(3, System.currentTimeMillis());
            insert.setString(4, batch.batchId().toString());
            insert.setInt(5, 0);
            insert.executeUpdate();
        }

        ExecutorService database = Executors.newSingleThreadExecutor();
        PendingRewardJournal replayJournal = new PendingRewardJournal(journalPath);
        PendingRewardRepository repository = new PendingRewardRepository(
                quietLogger("idempotent-replay"),
                databasePath.toFile(),
                database,
                new ManualDeliveryScheduler(),
                codec,
                replayJournal,
                Duration.ofMillis(200));
        try (Connection observer = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            assertEquals(1, countRows(observer, playerId));
            assertTrue(replayJournal.batches().isEmpty());
        } finally {
            repository.close();
        }
    }

    @Test
    void queueCompletionMaySynchronouslyCloseWithoutWaitingOnItself() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Path databasePath = tempDir.resolve("queue-callback-close.db");
            ExecutorService database = daemonDatabaseExecutor();
            PendingRewardRepository repository = new PendingRewardRepository(
                    quietLogger("queue-callback-close"),
                    databasePath.toFile(),
                    database,
                    Runnable::run,
                    new TextItemCodec());

            CompletionStage<QueueResult> closed = repository.queue(
                    UUID.randomUUID(), List.of(item(Material.STONE, 1)))
                    .whenComplete((result, failure) -> repository.close());

            assertEquals(QueueResult.STORED, await(closed));
            // 数据库线程内发起的关闭会延迟释放资源，二次关闭做有界等待，避免临时目录清理竞态。
            repository.close();
        });
    }

    @Test
    void claimCompletionMaySynchronouslyCloseWithoutWaitingOnItself() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Path databasePath = tempDir.resolve("claim-callback-close.db");
            ExecutorService database = daemonDatabaseExecutor();
            PendingRewardRepository repository = new PendingRewardRepository(
                    quietLogger("claim-callback-close"),
                    databasePath.toFile(),
                    database,
                    Runnable::run,
                    new TextItemCodec());

            ClaimResult claim = repository.claim(UUID.randomUUID(), items -> List.of());
            CompletionStage<Void> closed = claim.completion()
                    .whenComplete((ignored, failure) -> repository.close());

            await(closed);
            repository.close();
        });
    }

    @Test
    void closeCancelsScheduledDeliveryAndCompletesClaim() throws Exception {
        Path databasePath = tempDir.resolve("cancel-scheduled-delivery.db");
        ExecutorService database = Executors.newSingleThreadExecutor();
        ManualDeliveryScheduler deliveries = new ManualDeliveryScheduler();
        PendingRewardRepository repository = new PendingRewardRepository(
                quietLogger("cancel-scheduled-delivery"),
                databasePath.toFile(),
                database,
                deliveries,
                new TextItemCodec(),
                new PendingRewardJournal(tempDir.resolve("cancel-scheduled-delivery.bin")),
                Duration.ofMillis(200));
        UUID playerId = UUID.randomUUID();
        assertEquals(QueueResult.STORED, await(repository.queue(
                playerId, List.of(item(Material.STONE, 1)))));
        database.submit(() -> { }).get(5, SECONDS);
        ClaimResult claim = repository.claim(playerId, items -> List.of());
        database.submit(() -> { }).get(5, SECONDS);
        assertEquals(1, deliveries.size());

        repository.close();

        await(claim.completion());
        assertTrue(deliveries.first().cancelled);
    }

    @Test
    void concurrentCloseIsSingleFlightAndBoundedWithBlockedMigration() throws Exception {
        Path databasePath = tempDir.resolve("concurrent-close.db");
        Path journalPath = tempDir.resolve("concurrent-close.bin");
        PausedExecutorService database = new PausedExecutorService();
        CountingJournal journal = new CountingJournal(new PendingRewardJournal(journalPath));
        PendingRewardRepository repository = new PendingRewardRepository(
                quietLogger("concurrent-close"),
                databasePath.toFile(),
                database,
                new ManualDeliveryScheduler(),
                new TextItemCodec(),
                journal,
                Duration.ofMillis(100));
        CompletionStage<QueueResult> queued = repository.queue(
                UUID.randomUUID(), List.of(item(Material.DIAMOND, 1)));
        database.runNext();
        assertEquals(QueueResult.STORED, await(queued));
        CountDownLatch start = new CountDownLatch(1);
        Thread first = new Thread(() -> closeAfter(start, repository));
        Thread second = new Thread(() -> closeAfter(start, repository));
        first.start();
        second.start();
        start.countDown();

        first.join(2000L);
        second.join(2000L);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertEquals(1, journal.closeCount.get());
        try (PendingRewardJournal reopened = new PendingRewardJournal(journalPath)) {
            assertEquals(1, reopened.batches().size());
        }
    }

    @Test
    void timeoutDefersResourceClosureUntilRunningDatabaseTaskTerminates() throws Exception {
        Path databasePath = tempDir.resolve("deferred-close.db");
        Path journalPath = tempDir.resolve("deferred-close.bin");
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        ExecutorService database = daemonDatabaseExecutor();
        database.execute(() -> awaitIgnoringInterrupts(blockerStarted, releaseBlocker));
        assertTrue(blockerStarted.await(5, SECONDS));

        PendingRewardRepository repository = new PendingRewardRepository(
                quietLogger("deferred-close"),
                databasePath.toFile(),
                database,
                new ManualDeliveryScheduler(),
                new TextItemCodec(),
                new PendingRewardJournal(journalPath),
                Duration.ofMillis(100));
        try {
            // 入队任务排在被阻塞任务之后，关闭时会被 shutdownNow 丢弃并以 CLOSING 完成。
            CompletionStage<QueueResult> queued = repository.queue(
                    UUID.randomUUID(), List.of(item(Material.DIAMOND, 1)));

            assertTimeoutPreemptively(Duration.ofSeconds(1), repository::close);
            assertEquals(QueueResult.CLOSING, await(queued));
            assertThrows(IOException.class, () -> {
                try (PendingRewardJournal ignored = new PendingRewardJournal(journalPath)) {
                    // 成功打开即代表原仓储过早释放了资源锁。
                }
            });
            assertTimeoutPreemptively(Duration.ofSeconds(1), repository::close);

            releaseBlocker.countDown();
            assertTrue(database.awaitTermination(5, SECONDS));
            try (PendingRewardJournal reopened = awaitJournalOpen(journalPath, Duration.ofSeconds(2))) {
                assertTrue(reopened.batches().isEmpty());
            }
        } finally {
            releaseBlocker.countDown();
            database.shutdownNow();
            database.awaitTermination(5, SECONDS);
            repository.close();
        }
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(5, SECONDS);
    }

    private static Logger quietLogger(String name) {
        Logger logger = Logger.getLogger("PendingRewardRepositoryTest-" + name);
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static int countRows(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM pending_rewards WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.getInt(1);
            }
        }
    }

    private static ExecutorService daemonDatabaseExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "PendingRewardRepositoryTest-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void closeAfter(CountDownLatch start, PendingRewardRepository repository) {
        try {
            start.await(5, SECONDS);
            repository.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitIgnoringInterrupts(
            CountDownLatch started,
            CountDownLatch release) {
        started.countDown();
        boolean interrupted = false;
        while (release.getCount() > 0L) {
            try {
                release.await();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static PendingRewardJournal awaitJournalOpen(Path path, Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                return new PendingRewardJournal(path);
            } catch (IOException exception) {
                lastFailure = exception;
                Thread.sleep(10L);
            }
        }
        throw lastFailure == null ? new IOException("journal did not open") : lastFailure;
    }

    private static ItemStack item(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService database = Executors.newSingleThreadExecutor();
        private final ManualExecutor mainThread = new ManualExecutor();
        private final PendingRewardRepository.ItemCodec codec;
        private final PendingRewardRepository repository;
        private final Connection observer;

        private Fixture(Path databasePath) throws SQLException {
            this(databasePath, new TextItemCodec());
        }

        private Fixture(Path databasePath, PendingRewardRepository.ItemCodec codec) throws SQLException {
            this.codec = codec;
            Logger logger = Logger.getLogger("PendingRewardRepositoryTest-" + databasePath.getFileName());
            logger.setUseParentHandlers(false);
            repository = new PendingRewardRepository(
                    logger, databasePath.toFile(), database, mainThread, codec);
            observer = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        }

        private void awaitDatabase() throws Exception {
            database.submit(() -> { }).get(5, SECONDS);
        }

        private int countRows(UUID playerId) {
            return count("pending_rewards", playerId);
        }

        private int countQuarantinedRows(UUID playerId) {
            return count("pending_reward_quarantine", playerId);
        }

        private int count(String table, UUID playerId) {
            try (PreparedStatement statement = observer.prepareStatement(
                    "SELECT COUNT(*) FROM " + table + " WHERE player_uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.getInt(1);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private void insertRaw(UUID playerId, byte[] data) throws SQLException {
            try (PreparedStatement statement = observer.prepareStatement(
                    "INSERT INTO pending_rewards(player_uuid, item, created_at) VALUES(?, ?, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setBytes(2, data);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }

        private void createDiamondInsertFailureTrigger() throws SQLException {
            try (Statement statement = observer.createStatement()) {
                statement.executeUpdate("CREATE TRIGGER fail_diamond BEFORE INSERT ON pending_rewards "
                        + "WHEN CAST(NEW.item AS TEXT) LIKE 'DIAMOND:%' BEGIN "
                        + "SELECT RAISE(ABORT, 'diamond rejected'); END");
            }
        }

        private List<Long> rowIds(UUID playerId) throws SQLException {
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement statement = observer.prepareStatement(
                    "SELECT id FROM pending_rewards WHERE player_uuid = ? ORDER BY id")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        ids.add(result.getLong(1));
                    }
                }
            }
            return ids;
        }

        private List<StoredRow> rows(UUID playerId) throws Exception {
            List<StoredRow> rows = new ArrayList<>();
            try (PreparedStatement statement = observer.prepareStatement(
                    "SELECT id, item FROM pending_rewards WHERE player_uuid = ? ORDER BY id")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        rows.add(new StoredRow(result.getLong(1), codec.deserialize(result.getBytes(2))));
                    }
                }
            }
            return rows;
        }

        @Override
        public void close() throws Exception {
            repository.close();
            observer.close();
            assertTrue(database.isShutdown());
        }
    }

    private record StoredRow(long id, ItemStack item) {
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            tasks.add(command);
        }

        private synchronized void runNext() {
            tasks.remove().run();
        }

        private synchronized int size() {
            return tasks.size();
        }
    }

    private static final class ManualDeliveryScheduler
            implements PendingRewardRepository.DeliveryScheduler {
        private final Queue<ScheduledTask> tasks = new ArrayDeque<>();

        @Override
        public synchronized PendingRewardRepository.DeliveryHandle schedule(Runnable command) {
            ScheduledTask task = new ScheduledTask(command);
            tasks.add(task);
            return task;
        }

        private synchronized int size() {
            return tasks.size();
        }

        private synchronized ScheduledTask first() {
            return tasks.element();
        }

        private static final class ScheduledTask implements PendingRewardRepository.DeliveryHandle {
            private final Runnable command;
            private volatile boolean cancelled;

            private ScheduledTask(Runnable command) {
                this.command = command;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }

            @SuppressWarnings("unused")
            private void run() {
                if (!cancelled) {
                    command.run();
                }
            }
        }
    }

    private static final class PausedExecutorService extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public synchronized void shutdown() {
            shutdown = true;
            notifyAll();
        }

        @Override
        public synchronized List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            notifyAll();
            return pending;
        }

        @Override
        public synchronized boolean isShutdown() {
            return shutdown;
        }

        @Override
        public synchronized boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public synchronized boolean awaitTermination(long timeout, TimeUnit unit)
                throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (!isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
            return true;
        }

        @Override
        public synchronized void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor shut down");
            }
            tasks.add(command);
        }

        private void runNext() {
            Runnable task;
            synchronized (this) {
                task = tasks.remove();
            }
            task.run();
        }

        private synchronized int size() {
            return tasks.size();
        }
    }

    private static final class FailingAppendJournal implements RewardJournal {
        @Override
        public JournalBatch append(UUID playerId, List<byte[]> itemPayloads) throws IOException {
            throw new IOException("journal unavailable");
        }

        @Override
        public List<JournalBatch> batches() {
            return List.of();
        }

        @Override
        public void remove(UUID batchId) {
        }

        @Override
        public void close() {
        }
    }

    private static final class CountingJournal implements RewardJournal {
        private final RewardJournal delegate;
        private final AtomicInteger closeCount = new AtomicInteger();

        private CountingJournal(RewardJournal delegate) {
            this.delegate = delegate;
        }

        @Override
        public JournalBatch append(UUID playerId, List<byte[]> itemPayloads) throws IOException {
            return delegate.append(playerId, itemPayloads);
        }

        @Override
        public List<JournalBatch> batches() {
            return delegate.batches();
        }

        @Override
        public void remove(UUID batchId) throws IOException {
            delegate.remove(batchId);
        }

        @Override
        public void close() throws IOException {
            closeCount.incrementAndGet();
            delegate.close();
        }
    }

    private static class TextItemCodec implements PendingRewardRepository.ItemCodec {
        @Override
        public byte[] serialize(ItemStack item) throws IOException {
            return (item.getType().name() + ":" + item.getAmount()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public ItemStack deserialize(byte[] data) throws IOException {
            String[] parts = new String(data, StandardCharsets.UTF_8).split(":", -1);
            if (parts.length != 2) {
                throw new IOException("corrupt item");
            }
            try {
                return item(Material.valueOf(parts[0]), Integer.parseInt(parts[1]));
            } catch (IllegalArgumentException exception) {
                throw new IOException("corrupt item", exception);
            }
        }
    }

    private static final class FailingItemCodec extends TextItemCodec {
        private final Material failingMaterial;

        private FailingItemCodec(Material failingMaterial) {
            this.failingMaterial = failingMaterial;
        }

        @Override
        public byte[] serialize(ItemStack item) throws IOException {
            if (item.getType() == failingMaterial) {
                throw new IOException("serialization failed");
            }
            return super.serialize(item);
        }
    }
}
