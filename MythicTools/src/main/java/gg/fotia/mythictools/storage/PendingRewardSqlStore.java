package gg.fotia.mythictools.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 待领奖励的表结构、事务和持久去重；不调度任务或操作 Bukkit 实体。 */
final class PendingRewardSqlStore {
    private final Connection connection;
    private final Logger logger;

    PendingRewardSqlStore(Connection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;
    }

    void configureConnection() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS pending_rewards ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "player_uuid TEXT NOT NULL, item BLOB NOT NULL, created_at INTEGER NOT NULL, "
                    + "batch_id TEXT, item_index INTEGER)");
        }
        addColumnIfMissing("batch_id", "TEXT");
        addColumnIfMissing("item_index", "INTEGER");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_player "
                    + "ON pending_rewards(player_uuid)");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_pending_batch_item "
                    + "ON pending_rewards(batch_id, item_index) WHERE batch_id IS NOT NULL");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS pending_reward_batches ("
                    + "batch_id TEXT PRIMARY KEY, migrated_at INTEGER NOT NULL)");
            statement.executeUpdate("INSERT OR IGNORE INTO pending_reward_batches(batch_id, migrated_at) "
                    + "SELECT batch_id, MIN(created_at) FROM pending_rewards "
                    + "WHERE batch_id IS NOT NULL GROUP BY batch_id");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS pending_reward_quarantine ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, original_id INTEGER NOT NULL, "
                    + "player_uuid TEXT NOT NULL, item BLOB NOT NULL, created_at INTEGER NOT NULL, "
                    + "quarantined_at INTEGER NOT NULL, error TEXT NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_quarantine_player "
                    + "ON pending_reward_quarantine(player_uuid)");
        }
    }

    private void addColumnIfMissing(String column, String type) throws SQLException {
        boolean found = false;
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(pending_rewards)")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE pending_rewards ADD COLUMN " + column + " " + type);
            }
        }
    }

    void insertJournalBatchTransaction(JournalBatch batch) throws SQLException {
        synchronized (connection) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO pending_rewards("
                            + "player_uuid, item, created_at, batch_id, item_index) "
                            + "VALUES(?, ?, ?, ?, ?)")) {
                long createdAt = System.currentTimeMillis();
                try (PreparedStatement marker = connection.prepareStatement(
                        "INSERT OR IGNORE INTO pending_reward_batches(batch_id, migrated_at) VALUES(?, ?)")) {
                    marker.setString(1, batch.batchId().toString());
                    marker.setLong(2, createdAt);
                    if (marker.executeUpdate() == 0) {
                        connection.commit();
                        return;
                    }
                }
                List<byte[]> payloads = batch.payloadsView();
                for (int index = 0; index < payloads.size(); index++) {
                    insert.setString(1, batch.playerId().toString());
                    insert.setBytes(2, payloads.get(index));
                    insert.setLong(3, createdAt);
                    insert.setString(4, batch.batchId().toString());
                    insert.setInt(5, index);
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(exception);
                throw exception;
            } finally {
                restoreAutoCommit(previousAutoCommit);
            }
        }
    }

    void replaceRows(UUID playerId, List<Long> rows, List<byte[]> leftovers)
            throws SQLException {
        synchronized (connection) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteClaimedRows(playerId, rows);
                insertRows(playerId, leftovers);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(exception);
                throw exception;
            } finally {
                restoreAutoCommit(previousAutoCommit);
            }
        }
    }

    void quarantineRows(UUID playerId, List<CorruptRow> rows) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO pending_reward_quarantine("
                             + "original_id, player_uuid, item, created_at, quarantined_at, error) "
                             + "VALUES(?, ?, ?, ?, ?, ?)");
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM pending_rewards WHERE id = ? AND player_uuid = ?")) {
            long quarantinedAt = System.currentTimeMillis();
            for (CorruptRow row : rows) {
                insert.setLong(1, row.id());
                insert.setString(2, playerId.toString());
                insert.setBytes(3, row.bytes());
                insert.setLong(4, row.createdAt());
                insert.setLong(5, quarantinedAt);
                insert.setString(6, row.error());
                insert.executeUpdate();
                delete.setLong(1, row.id());
                delete.setString(2, playerId.toString());
                if (delete.executeUpdate() != 1) {
                    throw new SQLException("损坏奖励原始记录已发生变化: " + row.id());
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            rollback(exception);
            throw exception;
        } finally {
            restoreAutoCommit(previousAutoCommit);
        }
    }

    private void insertRows(UUID playerId, List<byte[]> serialized) throws SQLException {
        if (serialized.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO pending_rewards(player_uuid, item, created_at) VALUES(?, ?, ?)")) {
            long createdAt = System.currentTimeMillis();
            for (byte[] bytes : serialized) {
                insert.setString(1, playerId.toString());
                insert.setBytes(2, bytes);
                insert.setLong(3, createdAt);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void deleteClaimedRows(UUID playerId, List<Long> rows) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM pending_rewards WHERE id = ? AND player_uuid = ?")) {
            for (long id : rows) {
                delete.setLong(1, id);
                delete.setString(2, playerId.toString());
                if (delete.executeUpdate() != 1) {
                    throw new SQLException("待领取奖励原始记录已发生变化: " + id);
                }
            }
        }
    }

    private void rollback(Exception exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    private void restoreAutoCommit(boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "恢复 SQLite 自动提交失败", exception);
        }
    }

    record CorruptRow(long id, byte[] bytes, long createdAt, String error) {
    }
}
