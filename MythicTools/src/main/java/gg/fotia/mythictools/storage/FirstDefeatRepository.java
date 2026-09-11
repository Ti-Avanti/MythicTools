package gg.fotia.mythictools.storage;

import gg.fotia.mythictools.reward.FirstDefeatScope;
import gg.fotia.mythictools.reward.FirstDefeatSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 使用 SQLite 原子记录个人或全服首次击败。 */
public final class FirstDefeatRepository implements AutoCloseable {
    private static final String SERVER_OWNER = "SERVER";

    private final Connection connection;
    private final ExecutorService executor;
    private final java.util.concurrent.atomic.AtomicBoolean closing = new java.util.concurrent.atomic.AtomicBoolean();

    public FirstDefeatRepository(File databaseFile) throws SQLException {
        this(databaseFile, newExecutor());
    }

    FirstDefeatRepository(File databaseFile, ExecutorService executor) throws SQLException {
        this.executor = Objects.requireNonNull(executor, "executor");
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try {
            configureConnection();
            initializeSchema();
        } catch (SQLException exception) {
            connection.close();
            executor.shutdownNow();
            throw exception;
        }
    }

    /** 首个插入者返回 true；相同范围与来源的后续调用返回 false。 */
    public CompletionStage<Boolean> tryClaim(
            FirstDefeatScope scope,
            UUID playerId,
            FirstDefeatSource source) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(source, "source");
        String owner = scope == FirstDefeatScope.SERVER
                ? SERVER_OWNER
                : Objects.requireNonNull(playerId, "个人首次击败需要玩家 UUID").toString();
        return CompletableFuture.supplyAsync(() -> insert(scope, owner, source), executor);
    }

    private boolean insert(FirstDefeatScope scope, String owner, FirstDefeatSource source) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO first_defeat_claims("
                        + "scope, owner_key, source_type, source_id, claimed_at) VALUES(?, ?, ?, ?, ?)")) {
            statement.setString(1, scope.name());
            statement.setString(2, owner);
            statement.setString(3, source.type().name());
            statement.setString(4, source.id());
            statement.setLong(5, System.currentTimeMillis());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("无法写入首次击败记录", exception);
        }
    }

    /** 首次记录与待发奖励在同一事务中提交，关闭或重载不会留下无法恢复的领取标记。 */
    public CompletionStage<Boolean> enqueue(
            FirstDefeatScope scope, UUID playerId, FirstDefeatSource source, UUID rewardId, String payload) {
        String owner = scope == FirstDefeatScope.SERVER ? SERVER_OWNER : playerId.toString();
        return CompletableFuture.supplyAsync(() -> {
            try {
                connection.setAutoCommit(false);
                boolean accepted = insert(scope, owner, source);
                if (accepted) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO first_defeat_pending(id, payload, next_attempt_at) VALUES(?, ?, 0)")) {
                        statement.setString(1, rewardId.toString());
                        statement.setString(2, payload);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return accepted;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw new IllegalStateException("无法保存首次击败待发奖励", exception);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException exception) {
                    throw new IllegalStateException("无法恢复首次击败数据库事务", exception);
                }
            }
        }, executor);
    }

    public CompletionStage<List<PendingFirstDefeat>> pendingRewards() {
        return CompletableFuture.supplyAsync(() -> {
            List<PendingFirstDefeat> pending = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, payload FROM first_defeat_pending WHERE next_attempt_at <= ? "
                            + "ORDER BY next_attempt_at, rowid LIMIT 128")) {
                query.setLong(1, System.currentTimeMillis());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        pending.add(new PendingFirstDefeat(UUID.fromString(rows.getString(1)), rows.getString(2)));
                    }
                }
                return List.copyOf(pending);
            } catch (SQLException exception) {
                throw new IllegalStateException("无法读取首次击败待发奖励", exception);
            }
        }, executor);
    }

    public CompletionStage<Void> completeReward(UUID rewardId) {
        return updatePending("DELETE FROM first_defeat_pending WHERE id = ?", rewardId, 0L);
    }

    public CompletionStage<Void> retryReward(UUID rewardId, long retryAt) {
        return updatePending("UPDATE first_defeat_pending SET next_attempt_at = ? WHERE id = ?", rewardId, retryAt);
    }

    private CompletionStage<Void> updatePending(String sql, UUID rewardId, long retryAt) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (retryAt > 0L) {
                    statement.setLong(1, retryAt);
                    statement.setString(2, rewardId.toString());
                } else {
                    statement.setString(1, rewardId.toString());
                }
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("无法更新首次击败待发奖励", exception);
            }
        }, executor);
    }

    public record PendingFirstDefeat(UUID id, String payload) {
    }

    private void configureConnection() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS first_defeat_claims ("
                    + "scope TEXT NOT NULL, owner_key TEXT NOT NULL, source_type TEXT NOT NULL, "
                    + "source_id TEXT NOT NULL, claimed_at INTEGER NOT NULL, "
                    + "PRIMARY KEY(scope, owner_key, source_type, source_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS first_defeat_pending ("
                    + "id TEXT PRIMARY KEY, payload TEXT NOT NULL, next_attempt_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_first_defeat_retry "
                    + "ON first_defeat_pending(next_attempt_at)");
        }
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        executor.shutdown();
        boolean interrupted = false;
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                closeAfterTermination();
                return;
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            closeAfterTermination();
            Thread.currentThread().interrupt();
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法关闭首次击败数据库", exception);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void closeAfterTermination() {
        Thread closer = new Thread(() -> {
            boolean interrupted = false;
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            try {
                connection.close();
            } catch (SQLException exception) {
                java.util.logging.Logger.getLogger("MythicTools").log(
                        java.util.logging.Level.WARNING, "关闭首次击败数据库失败", exception);
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "MythicTools-FirstDefeat-Close");
        closer.setDaemon(true);
        closer.start();
    }

    private static ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "MythicTools-FirstDefeat-Database");
            thread.setDaemon(true);
            return thread;
        });
    }
}
