package gg.fotia.mythictools.blackboxtest;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** 使用短事务维护带 mttest 标记的 SQLite 测试记录，绝不改动真实待领取记录。 */
final class PendingRewardSqlStore {
    private static final String TEST_BATCH_PREFIX = "mttest:";
    private static final byte[] CORRUPT_PAYLOAD = "MTTEST_CORRUPT_V1".getBytes(StandardCharsets.UTF_8);
    private final Supplier<Path> databasePath;
    private final Path ownershipFile;
    private final Map<Long, UUID> ownedCorruptRows = new HashMap<>();

    PendingRewardSqlStore(Supplier<Path> databasePath) {
        this(databasePath, null);
    }

    PendingRewardSqlStore(Supplier<Path> databasePath, Path ownershipFile) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath");
        this.ownershipFile = ownershipFile;
        loadOwnership();
    }

    void seed(UUID playerId, List<byte[]> payloads) throws SQLException {
        insert(playerId, payloads);
    }

    void seedCorruptPair(UUID playerId, byte[] validPayload) throws SQLException {
        List<Long> ids = insert(playerId, List.of(validPayload, corruptPayload()));
        if (ids.size() != 2) {
            throw new SQLException("corrupt-pair-id-count-" + ids.size());
        }
        rememberCorruptRow(ids.get(1), playerId);
    }

    private List<Long> insert(UUID playerId, List<byte[]> payloads) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(payloads, "payloads");
        if (payloads.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        try (Connection connection = open()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO pending_rewards(player_uuid,item,created_at,batch_id,item_index) "
                            + "VALUES(?,?,?,?,?)");
                 Statement lastId = connection.createStatement()) {
                String batchId = TEST_BATCH_PREFIX + UUID.randomUUID();
                long createdAt = System.currentTimeMillis();
                for (int index = 0; index < payloads.size(); index++) {
                    statement.setString(1, playerId.toString());
                    statement.setBytes(2, payloads.get(index));
                    statement.setLong(3, createdAt);
                    statement.setString(4, batchId);
                    statement.setInt(5, index);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("pending-seed-insert-count");
                    }
                    try (ResultSet result = lastId.executeQuery("SELECT last_insert_rowid()")) {
                        if (!result.next()) {
                            throw new SQLException("pending-seed-missing-id");
                        }
                        ids.add(result.getLong(1));
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
        return List.copyOf(ids);
    }

    PendingRewardCounts inspect(UUID playerId) throws SQLException {
        return inspect(playerId, ignored -> false);
    }

    PendingRewardCounts inspect(UUID playerId, Predicate<byte[]> ownedPayload) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ownedPayload, "ownedPayload");
        try (Connection connection = open()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int[] pending = countPending(connection, playerId, ownedPayload);
                int[] quarantine = countQuarantine(connection, playerId);
                connection.commit();
                return new PendingRewardCounts(pending[0], quarantine[0], pending[1], quarantine[1]);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    void clear(UUID playerId) throws SQLException {
        clear(playerId, ignored -> false);
    }

    void clear(UUID playerId, Predicate<byte[]> ownedPayload) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ownedPayload, "ownedPayload");
        try (Connection connection = open()) {
            boolean transaction = false;
            try (PreparedStatement pending = connection.prepareStatement(
                         "DELETE FROM pending_rewards WHERE id=? AND player_uuid=?");
                 PreparedStatement quarantine = connection.prepareStatement(
                         "DELETE FROM pending_reward_quarantine WHERE player_uuid=? AND original_id=?")) {
                execute(connection, "BEGIN IMMEDIATE");
                transaction = true;
                for (long id : ownedPendingRowIds(connection, playerId, ownedPayload)) {
                    pending.setLong(1, id);
                    pending.setString(2, playerId.toString());
                    pending.addBatch();
                }
                pending.executeBatch();
                for (long id : ownedCorruptRowIds(playerId)) {
                    quarantine.setString(1, playerId.toString());
                    quarantine.setLong(2, id);
                    quarantine.addBatch();
                }
                quarantine.executeBatch();
                execute(connection, "COMMIT");
                transaction = false;
                forgetCorruptRows(playerId);
            } catch (SQLException | RuntimeException exception) {
                if (transaction) {
                    try {
                        execute(connection, "ROLLBACK");
                    } catch (SQLException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                }
                throw exception;
            }
        }
    }

    static byte[] corruptPayload() {
        return CORRUPT_PAYLOAD.clone();
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.get().toAbsolutePath());
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=250");
            }
            return connection;
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static int[] countPending(
            Connection connection,
            UUID playerId,
            Predicate<byte[]> ownedPayload) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT batch_id,item FROM pending_rewards WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                int total = 0;
                int owned = 0;
                while (result.next()) {
                    total++;
                    String batchId = result.getString("batch_id");
                    if (isTestBatch(batchId) || isOwnedPayload(ownedPayload, result.getBytes("item"))) {
                        owned++;
                    }
                }
                return new int[]{total, owned};
            }
        }
    }

    private static List<Long> ownedPendingRowIds(
            Connection connection,
            UUID playerId,
            Predicate<byte[]> ownedPayload) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,batch_id,item FROM pending_rewards WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String batchId = result.getString("batch_id");
                    if (isTestBatch(batchId) || isOwnedPayload(ownedPayload, result.getBytes("item"))) {
                        ids.add(result.getLong("id"));
                    }
                }
            }
        }
        return ids;
    }

    private static boolean isTestBatch(String batchId) {
        return batchId != null && batchId.startsWith(TEST_BATCH_PREFIX);
    }

    private static boolean isOwnedPayload(Predicate<byte[]> predicate, byte[] payload) {
        try {
            return predicate.test(payload);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private int[] countQuarantine(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT original_id FROM pending_reward_quarantine WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                int total = 0;
                int owned = 0;
                Map<Long, UUID> ownership = ownershipSnapshot();
                while (result.next()) {
                    total++;
                    if (playerId.equals(ownership.get(result.getLong("original_id")))) {
                        owned++;
                    }
                }
                return new int[]{total, owned};
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private synchronized void rememberCorruptRow(long rowId, UUID playerId) throws SQLException {
        ownedCorruptRows.put(rowId, playerId);
        persistOwnership();
    }

    private synchronized List<Long> ownedCorruptRowIds(UUID playerId) {
        return ownedCorruptRows.entrySet().stream()
                .filter(entry -> playerId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private synchronized Map<Long, UUID> ownershipSnapshot() {
        return Map.copyOf(ownedCorruptRows);
    }

    private synchronized void forgetCorruptRows(UUID playerId) throws SQLException {
        ownedCorruptRows.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
        persistOwnership();
    }

    private synchronized void loadOwnership() {
        if (ownershipFile == null || !Files.isRegularFile(ownershipFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(ownershipFile, StandardCharsets.UTF_8)) {
                String[] parts = line.trim().split(" ", 2);
                if (parts.length == 2) {
                    ownedCorruptRows.put(Long.parseLong(parts[0]), UUID.fromString(parts[1]));
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("invalid-pending-ownership-file", exception);
        }
    }

    private synchronized void persistOwnership() throws SQLException {
        if (ownershipFile == null) {
            return;
        }
        try {
            Path parent = ownershipFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = ownershipFile.resolveSibling(ownershipFile.getFileName() + ".tmp");
            String content = ownedCorruptRows.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .collect(Collectors.joining(System.lineSeparator(), "", ownedCorruptRows.isEmpty()
                            ? "" : System.lineSeparator()));
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, ownershipFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, ownershipFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (java.io.IOException exception) {
            throw new SQLException("cannot-persist-pending-ownership", exception);
        }
    }
}
