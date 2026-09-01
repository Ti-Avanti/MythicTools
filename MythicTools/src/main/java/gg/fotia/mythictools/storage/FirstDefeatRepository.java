package gg.fotia.mythictools.storage;

import gg.fotia.mythictools.reward.FirstDefeatScope;
import gg.fotia.mythictools.reward.FirstDefeatSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        boolean interrupted = false;
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            executor.shutdownNow();
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

    private static ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "MythicTools-FirstDefeat-Database");
            thread.setDaemon(true);
            return thread;
        });
    }
}
