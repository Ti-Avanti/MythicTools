package gg.fotia.mythictools.storage;

import gg.fotia.mythictools.runtime.OwnedTasks;
import gg.fotia.mythictools.storage.PendingRewardSqlStore.CorruptRow;
import gg.fotia.mythictools.runtime.BukkitTaskScheduler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** 使用 SQLite 保存离线玩家待领取的完整 ItemStack。 */
public final class PendingRewardRepository implements PendingRewardQueue, AutoCloseable {
    private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(5L);
    /** 单次领取读取的最大行数；超过时结算完成后链式续读，避免超大积压卡住主线程交付。 */
    private static final int CLAIM_BATCH_LIMIT = 512;

    private final Logger logger;
    private final ExecutorService databaseExecutor;
    private final DeliveryScheduler deliveryScheduler;
    private final Connection connection;
    private final PendingRewardSqlStore sql;
    private final ItemCodec codec;
    private final RewardJournal journal;
    private final Duration closeTimeout;
    private final java.util.concurrent.ScheduledExecutorService migrationRetryScheduler =
            newMigrationRetryScheduler();
    private final Map<UUID, CompletableFuture<Void>> activeClaims = new HashMap<>();
    private final Map<UUID, ScheduledDelivery> scheduledDeliveries = new HashMap<>();
    private final List<CompletableFuture<QueueResult>> pendingQueueResults = new ArrayList<>();
    private final Object lifecycleMonitor = new Object();
    private final ThreadLocal<Boolean> databaseTaskThread = ThreadLocal.withInitial(() -> false);
    private final CompletableFuture<Void> closeCompletion = new CompletableFuture<>();

    private boolean closing;
    private boolean closeStarted;
    private boolean closed;
    private Thread closeOwner;
    private int admittedDatabaseTasks;
    private int activeDeliveries;

    public PendingRewardRepository(JavaPlugin plugin, File databaseFile) throws SQLException {
        this(plugin, databaseFile, new OwnedTasks(new BukkitTaskScheduler(plugin)));
    }

    public PendingRewardRepository(JavaPlugin plugin, File databaseFile, OwnedTasks tasks) throws SQLException {
        this(
                plugin.getLogger(),
                databaseFile,
                newDatabaseExecutor(),
                command -> {
                    var handle = tasks.execute(command);
                    return handle::cancel;
                },
                new BukkitItemCodec(),
                openJournal(databaseFile),
                DEFAULT_CLOSE_TIMEOUT);
    }

    PendingRewardRepository(
            Logger logger,
            File databaseFile,
            ExecutorService databaseExecutor,
            Executor mainThreadExecutor,
            ItemCodec codec) throws SQLException {
        this(
                logger,
                databaseFile,
                databaseExecutor,
                command -> {
                    mainThreadExecutor.execute(command);
                    return () -> { };
                },
                codec,
                openJournal(databaseFile),
                DEFAULT_CLOSE_TIMEOUT);
    }

    PendingRewardRepository(
            Logger logger,
            File databaseFile,
            ExecutorService databaseExecutor,
            DeliveryScheduler deliveryScheduler,
            ItemCodec codec,
            RewardJournal journal,
            Duration closeTimeout) throws SQLException {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.deliveryScheduler = Objects.requireNonNull(deliveryScheduler, "deliveryScheduler");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.closeTimeout = requirePositive(closeTimeout);

        Connection opened;
        try {
            opened = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        } catch (SQLException exception) {
            closeJournalAfterConstructionFailure(exception);
            migrationRetryScheduler.shutdownNow();
            databaseExecutor.shutdownNow();
            throw exception;
        }
        connection = opened;
        sql = new PendingRewardSqlStore(connection, logger);
        try {
            sql.configureConnection();
            sql.initializeSchema();
            replayJournal();
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            closeJournalAfterConstructionFailure(exception);
            migrationRetryScheduler.shutdownNow();
            databaseExecutor.shutdownNow();
            throw exception;
        }
    }

    /** 序列化、日志强制落盘与 SQLite 迁移全部在数据库线程完成；返回的 Future 在日志接管后完成。 */
    @Override
    public CompletionStage<QueueResult> queue(UUID playerId, List<ItemStack> items) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(QueueResult.STORED);
        }
        List<ItemStack> snapshots = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            snapshots.add(Objects.requireNonNull(item, "item").clone());
        }
        CompletableFuture<QueueResult> result = new CompletableFuture<>();
        synchronized (lifecycleMonitor) {
            if (closing) {
                return CompletableFuture.completedFuture(QueueResult.CLOSING);
            }
            admittedDatabaseTasks++;
            try {
                databaseExecutor.execute(() -> runQueueTask(playerId, snapshots, result));
            } catch (RejectedExecutionException exception) {
                admittedDatabaseTasks--;
                lifecycleMonitor.notifyAll();
                logger.log(Level.WARNING, "数据库执行器拒绝离线奖励入队", exception);
                return CompletableFuture.completedFuture(QueueResult.CLOSING);
            }
            pendingQueueResults.add(result);
        }
        return result;
    }

    private void runQueueTask(UUID playerId, List<ItemStack> items, CompletableFuture<QueueResult> result) {
        databaseTaskThread.set(true);
        synchronized (lifecycleMonitor) {
            // 已开始的写入由实际落盘结果完成，关闭超时不能提前触发重复补偿。
            pendingQueueResults.remove(result);
        }
        QueueResult outcome;
        try {
            try {
                outcome = performQueue(playerId, items);
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "离线奖励入队发生意外错误", exception);
                outcome = QueueResult.JOURNAL_FAILED;
            }
        } finally {
            finishDatabaseTask();
        }
        try {
            synchronized (lifecycleMonitor) {
                pendingQueueResults.remove(result);
            }
            result.complete(outcome);
        } finally {
            databaseTaskThread.remove();
        }
    }

    /** 日志强制落盘成功即代表仓储接管所有权，SQLite 迁移作为后续任务异步完成。 */
    private QueueResult performQueue(UUID playerId, List<ItemStack> items) {
        List<byte[]> serialized;
        try {
            serialized = serializeAll(items);
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.SEVERE, "无法序列化整批离线奖励，未写入任何记录", exception);
            return QueueResult.SERIALIZATION_FAILED;
        }
        synchronized (lifecycleMonitor) {
            if (closing) {
                return QueueResult.CLOSING;
            }
        }
        JournalBatch batch;
        try {
            // 已接纳任务计数保证资源存活；文件 I/O 不持有主线程也要获取的生命周期锁。
            batch = journal.append(playerId, serialized);
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.SEVERE, "无法将离线奖励强制写入持久日志", exception);
            return QueueResult.JOURNAL_FAILED;
        }
        synchronized (lifecycleMonitor) {
            if (!closing) {
                submitMigrationLocked(batch);
            }
        }
        return QueueResult.STORED;
    }

    @Override
    public CompletionStage<QueueResult> retainForRetry(UUID playerId, List<ItemStack> items) {
        return queue(playerId, items);
    }

    /**
     * 接纳 claim 后先读取并隔离坏行，再在主线程交付。背包变更到数据库结算之间为至少一次语义；
     * 进程若在该窗口崩溃，原行会在下次领取时再次交付。
     */
    @Override
    public ClaimResult claim(UUID playerId, PendingRewardDelivery delivery) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(delivery, "delivery");
        CompletableFuture<Void> completion = new CompletableFuture<>();
        ClaimResult.Status status;
        boolean completeImmediately = false;
        synchronized (lifecycleMonitor) {
            if (closing) {
                status = ClaimResult.Status.CLOSING;
                completeImmediately = true;
            } else {
                CompletableFuture<Void> existing = activeClaims.get(playerId);
                if (existing != null) {
                    return new ClaimResult(ClaimResult.Status.BUSY, existing);
                }
                activeClaims.put(playerId, completion);
                admittedDatabaseTasks++;
                try {
                    databaseExecutor.execute(() -> runClaimLoadTask(
                            playerId, delivery, completion, new ClaimCursor(0L, -1L)));
                    status = ClaimResult.Status.ACCEPTED;
                } catch (RejectedExecutionException exception) {
                    admittedDatabaseTasks--;
                    activeClaims.remove(playerId, completion);
                    lifecycleMonitor.notifyAll();
                    logger.log(Level.WARNING, "数据库执行器拒绝领取离线奖励", exception);
                    status = ClaimResult.Status.CLOSING;
                    completeImmediately = true;
                }
            }
        }
        if (completeImmediately) {
            completion.complete(null);
        }
        return new ClaimResult(status, completion);
    }

    /** 启动重放：全部批次先入库，再一次性压缩日志，避免逐批全量重写。 */
    private void replayJournal() {
        List<JournalBatch> batches = journal.batches();
        if (batches.isEmpty()) {
            return;
        }
        List<UUID> migrated = new ArrayList<>();
        for (JournalBatch batch : batches) {
            try {
                sql.insertJournalBatchTransaction(batch);
                migrated.add(batch.batchId());
            } catch (SQLException | RuntimeException exception) {
                logger.log(Level.SEVERE, "无法将持久日志迁移到 SQLite，稍后自动重试", exception);
                scheduleMigrationRetry(batch, 1);
            }
        }
        try {
            journal.removeAll(migrated);
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.SEVERE, "压缩离线奖励持久日志失败，重复记录将在下次重放时被幂等忽略", exception);
        }
    }

    private void submitMigrationLocked(JournalBatch batch) {
        admittedDatabaseTasks++;
        try {
            databaseExecutor.execute(() -> runMigrationTask(batch, 1));
        } catch (RuntimeException exception) {
            admittedDatabaseTasks--;
            lifecycleMonitor.notifyAll();
            logger.log(Level.WARNING, "数据库执行器拒绝迁移离线奖励，持久日志将继续保留", exception);
        }
    }

    private void runMigrationTask(JournalBatch batch, int attempt) {
        databaseTaskThread.set(true);
        try {
            migrateBatch(batch, attempt);
        } finally {
            finishDatabaseTask();
            databaseTaskThread.remove();
        }
    }

    private void migrateBatch(JournalBatch batch, int attempt) {
        try {
            sql.insertJournalBatchTransaction(batch);
            journal.remove(batch.batchId());
            if (attempt > 1) {
                logger.info("离线奖励日志批次已在第 " + attempt + " 次尝试后成功迁移: " + batch.batchId());
            }
        } catch (SQLException | IOException | RuntimeException exception) {
            logger.log(Level.SEVERE,
                    "无法将持久日志迁移到 SQLite（第 " + attempt + " 次尝试），稍后自动重试", exception);
            scheduleMigrationRetry(batch, attempt);
        }
    }

    /** 指数退避重试，5 秒起步、封顶 5 分钟；关闭后由下次启动重放接管。 */
    private void scheduleMigrationRetry(JournalBatch batch, int failedAttempts) {
        try {
            migrationRetryScheduler.schedule(
                    () -> resubmitMigration(batch, failedAttempts + 1),
                    retryDelayMillis(failedAttempts),
                    TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            logger.warning("迁移重试调度器已关闭，日志批次将保留到重启后重放: " + batch.batchId());
        }
    }

    private void resubmitMigration(JournalBatch batch, int attempt) {
        synchronized (lifecycleMonitor) {
            if (closing) {
                return;
            }
            admittedDatabaseTasks++;
            try {
                databaseExecutor.execute(() -> runMigrationTask(batch, attempt));
            } catch (RejectedExecutionException exception) {
                admittedDatabaseTasks--;
                lifecycleMonitor.notifyAll();
                logger.warning("数据库执行器拒绝迁移重试，日志批次将保留到重启后重放: " + batch.batchId());
            }
        }
    }

    private static long retryDelayMillis(int failedAttempts) {
        long exponent = Math.min(6L, failedAttempts - 1L);
        return Math.min(300_000L, 5_000L << exponent);
    }

    private void runClaimLoadTask(
            UUID playerId,
            PendingRewardDelivery delivery,
            CompletableFuture<Void> completion,
            ClaimCursor cursor) {
        databaseTaskThread.set(true);
        DeliveryWork work = null;
        try {
            try {
                while (true) {
                    LoadResult loaded = loadClaim(playerId, delivery, completion, cursor);
                    cursor = loaded.cursor();
                    work = loaded.work();
                    if (work != null || !loaded.retryLoad()) {
                        break;
                    }
                }
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "读取离线奖励时发生意外错误，原始记录已保留", exception);
            }
        } finally {
            finishDatabaseTask();
        }
        try {
            if (work == null || !scheduleDelivery(work)) {
                finishClaim(playerId, completion);
            }
        } finally {
            databaseTaskThread.remove();
        }
    }

    private LoadResult loadClaim(
            UUID playerId,
            PendingRewardDelivery delivery,
            CompletableFuture<Void> completion,
            ClaimCursor cursor) {
        List<PendingRow> validRows = new ArrayList<>();
        List<CorruptRow> corruptRows = new ArrayList<>();
        long upperId = cursor.upperId();
        long lastId = cursor.afterId();
        synchronized (connection) {
            try {
                if (upperId < 0L) {
                    try (PreparedStatement maximum = connection.prepareStatement(
                            "SELECT COALESCE(MAX(id), 0) FROM pending_rewards WHERE player_uuid = ?")) {
                        maximum.setString(1, playerId.toString());
                        try (ResultSet result = maximum.executeQuery()) {
                            upperId = result.next() ? result.getLong(1) : 0L;
                        }
                    }
                }
                try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, item, created_at FROM pending_rewards "
                            + "WHERE player_uuid = ? AND id > ? AND id <= ? ORDER BY id LIMIT " + CLAIM_BATCH_LIMIT)) {
                query.setString(1, playerId.toString());
                query.setLong(2, lastId);
                query.setLong(3, upperId);
                try (ResultSet results = query.executeQuery()) {
                    while (results.next()) {
                        long id = results.getLong("id");
                        lastId = id;
                        byte[] bytes = results.getBytes("item");
                        try {
                            ItemStack item = Objects.requireNonNull(codec.deserialize(bytes), "decoded item");
                            validRows.add(new PendingRow(id, item));
                        } catch (IOException | ClassNotFoundException | RuntimeException exception) {
                            corruptRows.add(new CorruptRow(
                                    id,
                                    bytes,
                                    results.getLong("created_at"),
                                    diagnostic(exception)));
                            logger.log(Level.SEVERE, "隔离损坏的离线奖励记录 id=" + id, exception);
                        }
                    }
                }
                if (!corruptRows.isEmpty()) {
                    sql.quarantineRows(playerId, corruptRows);
                }
                }
            } catch (SQLException exception) {
                logger.log(Level.SEVERE, "无法读取或隔离离线奖励，原始有效记录已保留", exception);
                return new LoadResult(null, false, cursor);
            }
        }

        ClaimCursor next = new ClaimCursor(lastId, upperId);
        boolean batchFull = lastId < upperId && validRows.size() + corruptRows.size() >= CLAIM_BATCH_LIMIT;
        if (validRows.isEmpty()) {
            // 整批都是损坏行且已隔离时继续读取下一批，隔离保证了循环单调推进。
            return new LoadResult(null, batchFull, next);
        }
        List<ItemStack> items = validRows.stream().map(PendingRow::item).toList();
        return new LoadResult(
                new DeliveryWork(playerId, validRows, items, delivery, completion, batchFull, next), false, next);
    }

    private boolean scheduleDelivery(DeliveryWork work) {
        ScheduledDelivery scheduled = new ScheduledDelivery(work);
        synchronized (lifecycleMonitor) {
            if (closing) {
                return false;
            }
            scheduledDeliveries.put(work.playerId(), scheduled);
        }
        try {
            DeliveryHandle handle = deliveryScheduler.schedule(() -> runScheduledDelivery(scheduled));
            scheduled.attach(handle);
            return true;
        } catch (RuntimeException exception) {
            boolean started;
            synchronized (lifecycleMonitor) {
                scheduledDeliveries.remove(work.playerId(), scheduled);
                started = scheduled.started;
                lifecycleMonitor.notifyAll();
            }
            logger.log(Level.SEVERE, "无法调度主线程离线奖励交付，原始记录已保留", exception);
            return started;
        }
    }

    private void runScheduledDelivery(ScheduledDelivery scheduled) {
        DeliveryWork work = scheduled.work;
        boolean cancelled;
        synchronized (lifecycleMonitor) {
            scheduledDeliveries.remove(work.playerId(), scheduled);
            cancelled = closing || scheduled.cancelled;
            if (cancelled) {
                lifecycleMonitor.notifyAll();
            } else {
                scheduled.started = true;
                activeDeliveries++;
            }
        }
        if (cancelled) {
            finishClaim(work.playerId(), work.completion());
            return;
        }

        boolean settlementSubmitted = false;
        try {
            long attemptedAmount = work.items().stream().mapToLong(ItemStack::getAmount).sum();
            List<ItemStack> leftovers = Objects.requireNonNull(
                    work.delivery().deliver(List.copyOf(work.items())), "delivery leftovers");
            List<byte[]> serializedLeftovers = List.copyOf(serializeAll(leftovers));
            boolean progressed = leftovers.stream().mapToLong(ItemStack::getAmount).sum() < attemptedAmount;
            settlementSubmitted = submitSettlement(work, serializedLeftovers, progressed);
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.SEVERE, "离线奖励交付或剩余物品快照失败，原始记录已保留", exception);
        } finally {
            endDelivery();
            if (!settlementSubmitted) {
                finishClaim(work.playerId(), work.completion());
            }
        }
    }

    private boolean submitSettlement(DeliveryWork work, List<byte[]> serializedLeftovers, boolean progressed) {
        synchronized (lifecycleMonitor) {
            if (closing) {
                return false;
            }
            admittedDatabaseTasks++;
            try {
                databaseExecutor.execute(
                        () -> runSettlementTask(work, serializedLeftovers, progressed));
                return true;
            } catch (RejectedExecutionException exception) {
                admittedDatabaseTasks--;
                lifecycleMonitor.notifyAll();
                logger.log(Level.SEVERE, "数据库执行器拒绝结算离线奖励，原始记录已保留", exception);
                return false;
            }
        }
    }

    private void runSettlementTask(DeliveryWork work, List<byte[]> serializedLeftovers, boolean progressed) {
        databaseTaskThread.set(true);
        boolean continueClaim = false;
        try {
            try {
                sql.replaceRows(work.playerId(), work.rows().stream().map(PendingRow::id).toList(), serializedLeftovers);
                continueClaim = work.mayHaveMore() && progressed;
            } catch (SQLException | RuntimeException exception) {
                logger.log(Level.SEVERE, "无法结算离线奖励，原始记录已保留", exception);
            }
        } finally {
            finishDatabaseTask();
            try {
                if (continueClaim) {
                    continueClaimAfterSettlement(work);
                } else {
                    finishClaim(work.playerId(), work.completion());
                }
            } finally {
                databaseTaskThread.remove();
            }
        }
    }

    /** 一批结算完成后可能仍有剩余行，链式发起下一批读取直至清空。 */
    private void continueClaimAfterSettlement(DeliveryWork work) {
        synchronized (lifecycleMonitor) {
            if (!closing) {
                admittedDatabaseTasks++;
                try {
                    databaseExecutor.execute(() -> runClaimLoadTask(
                            work.playerId(), work.delivery(), work.completion(), work.cursor()));
                    return;
                } catch (RejectedExecutionException exception) {
                    admittedDatabaseTasks--;
                    lifecycleMonitor.notifyAll();
                    logger.log(Level.WARNING, "数据库执行器拒绝继续领取剩余离线奖励", exception);
                }
            }
        }
        finishClaim(work.playerId(), work.completion());
    }

    private List<byte[]> serializeAll(List<ItemStack> items) throws IOException {
        List<byte[]> serialized = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            serialized.add(codec.serialize(Objects.requireNonNull(item, "item")));
        }
        return List.copyOf(serialized);
    }

    private void endDelivery() {
        synchronized (lifecycleMonitor) {
            if (activeDeliveries > 0) {
                activeDeliveries--;
            }
            lifecycleMonitor.notifyAll();
        }
    }

    private void finishDatabaseTask() {
        synchronized (lifecycleMonitor) {
            if (admittedDatabaseTasks > 0) {
                admittedDatabaseTasks--;
            }
            lifecycleMonitor.notifyAll();
        }
    }

    private void finishClaim(UUID playerId, CompletableFuture<Void> completion) {
        synchronized (lifecycleMonitor) {
            activeClaims.remove(playerId, completion);
            lifecycleMonitor.notifyAll();
        }
        completion.complete(null);
    }

    @Override
    public void close() {
        List<ScheduledDelivery> cancellations;
        boolean owner;
        synchronized (lifecycleMonitor) {
            if (closed) {
                return;
            }
            if (closeStarted) {
                if (closeOwner == Thread.currentThread()) {
                    return;
                }
                owner = false;
                cancellations = List.of();
            } else {
                closeStarted = true;
                closing = true;
                closeOwner = Thread.currentThread();
                owner = true;
                cancellations = new ArrayList<>(scheduledDeliveries.values());
                scheduledDeliveries.clear();
                cancellations.forEach(ScheduledDelivery::markCancelled);
                lifecycleMonitor.notifyAll();
            }
        }
        if (!owner) {
            awaitCloseCompletion();
            return;
        }
        migrationRetryScheduler.shutdownNow();

        boolean interrupted = false;
        long deadline = System.nanoTime() + closeTimeout.toNanos();
        for (ScheduledDelivery scheduled : cancellations) {
            try {
                scheduled.cancel();
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "取消待执行的离线奖励交付失败", exception);
            }
            finishClaim(scheduled.work.playerId(), scheduled.work.completion());
        }

        // 数据库线程自身触发关闭时不能等待队列排空，否则会等待自己正占用的线程。
        boolean onDatabaseThread = databaseTaskThread.get();
        boolean quiescent;
        synchronized (lifecycleMonitor) {
            while (!onDatabaseThread && (admittedDatabaseTasks > 0 || activeDeliveries > 0)) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    break;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(lifecycleMonitor, remaining);
                } catch (InterruptedException exception) {
                    interrupted = true;
                    break;
                }
            }
            quiescent = admittedDatabaseTasks == 0 && activeDeliveries == 0;
        }

        databaseExecutor.shutdown();
        if (!quiescent) {
            logger.warning("等待离线奖励任务结束超时，资源将在数据库执行器真正终止后关闭");
            databaseExecutor.shutdownNow();
        } else if (!onDatabaseThread) {
            long remaining = Math.max(0L, deadline - System.nanoTime());
            try {
                if (!databaseExecutor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                interrupted = true;
                databaseExecutor.shutdownNow();
            }
        }

        completeUnfinishedClaimsAfterReleasingCounters();
        synchronized (lifecycleMonitor) {
            closeOwner = null;
            lifecycleMonitor.notifyAll();
        }

        if (databaseExecutor.isTerminated()) {
            finalizeResources();
        } else {
            startDeferredResourceFinalizer();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void completeUnfinishedClaimsAfterReleasingCounters() {
        List<CompletableFuture<Void>> unfinishedClaims;
        List<CompletableFuture<QueueResult>> unfinishedQueues;
        synchronized (lifecycleMonitor) {
            admittedDatabaseTasks = 0;
            activeDeliveries = 0;
            unfinishedClaims = new ArrayList<>(activeClaims.values());
            activeClaims.clear();
            unfinishedQueues = new ArrayList<>(pendingQueueResults);
            pendingQueueResults.clear();
            lifecycleMonitor.notifyAll();
        }
        unfinishedClaims.forEach(completion -> completion.complete(null));
        unfinishedQueues.forEach(result -> result.complete(QueueResult.CLOSING));
    }

    private void startDeferredResourceFinalizer() {
        Thread finalizer = new Thread(() -> {
            boolean interrupted = false;
            while (!databaseExecutor.isTerminated()) {
                try {
                    databaseExecutor.awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            finalizeResources();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "MythicTools-PendingReward-Close");
        finalizer.setDaemon(true);
        finalizer.start();
    }

    private void finalizeResources() {
        try {
            connection.close();
        } catch (SQLException | RuntimeException exception) {
            logger.log(Level.WARNING, "关闭 SQLite 连接失败", exception);
        }
        try {
            journal.close();
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.WARNING, "关闭离线奖励持久日志失败", exception);
        }
        synchronized (lifecycleMonitor) {
            closed = true;
            lifecycleMonitor.notifyAll();
        }
        closeCompletion.complete(null);
    }

    private void awaitCloseCompletion() {
        boolean interrupted = false;
        try {
            closeCompletion.get(closeTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            interrupted = true;
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException ignored) {
            // 首个关闭调用拥有清理职责；并发调用只做有界等待。
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeJournalAfterConstructionFailure(Exception original) {
        try {
            journal.close();
        } catch (IOException closeException) {
            original.addSuppressed(closeException);
        }
    }

    private static RewardJournal openJournal(File databaseFile) throws SQLException {
        Path databasePath = databaseFile.toPath();
        Path journalPath = databasePath.resolveSibling(databaseFile.getName() + ".pending-rewards.journal");
        try {
            return new PendingRewardJournal(journalPath);
        } catch (IOException exception) {
            throw new SQLException("无法打开离线奖励持久日志", exception);
        }
    }

    private static Duration requirePositive(Duration timeout) {
        Objects.requireNonNull(timeout, "closeTimeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("closeTimeout must be positive");
        }
        return timeout;
    }

    private static String diagnostic(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getName() + (message == null ? "" : ": " + message);
    }

    private static ExecutorService newDatabaseExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MythicTools-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static java.util.concurrent.ScheduledExecutorService newMigrationRetryScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MythicTools-MigrationRetry");
            thread.setDaemon(true);
            return thread;
        });
    }

    private record PendingRow(long id, ItemStack item) {
    }

    private record DeliveryWork(
            UUID playerId,
            List<PendingRow> rows,
            List<ItemStack> items,
            PendingRewardDelivery delivery,
            CompletableFuture<Void> completion,
            boolean mayHaveMore,
            ClaimCursor cursor) {
        private DeliveryWork {
            rows = List.copyOf(rows);
            items = List.copyOf(items);
        }
    }

    private record ClaimCursor(long afterId, long upperId) {
    }

    private record LoadResult(DeliveryWork work, boolean retryLoad, ClaimCursor cursor) {
    }

    private static final class ScheduledDelivery {
        private final DeliveryWork work;
        private volatile DeliveryHandle handle;
        private volatile boolean cancelled;
        private volatile boolean started;

        private ScheduledDelivery(DeliveryWork work) {
            this.work = work;
        }

        private void attach(DeliveryHandle handle) {
            this.handle = Objects.requireNonNull(handle, "delivery handle");
            if (cancelled) {
                handle.cancel();
            }
        }

        private void markCancelled() {
            cancelled = true;
        }

        private void cancel() {
            markCancelled();
            DeliveryHandle current = handle;
            if (current != null) {
                current.cancel();
            }
        }
    }

    interface ItemCodec {
        byte[] serialize(ItemStack item) throws IOException;

        ItemStack deserialize(byte[] data) throws IOException, ClassNotFoundException;
    }

    @FunctionalInterface
    interface DeliveryScheduler {
        DeliveryHandle schedule(Runnable task);
    }

    @FunctionalInterface
    interface DeliveryHandle {
        void cancel();
    }

    private static final class BukkitItemCodec implements ItemCodec {
        @Override
        public byte[] serialize(ItemStack item) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(output)) {
                stream.writeObject(item);
            }
            return output.toByteArray();
        }

        @Override
        public ItemStack deserialize(byte[] data) throws IOException, ClassNotFoundException {
            try (BukkitObjectInputStream stream = new BukkitObjectInputStream(
                    new ByteArrayInputStream(data))) {
                return (ItemStack) stream.readObject();
            }
        }
    }
}
