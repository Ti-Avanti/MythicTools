package gg.fotia.mythictools.blackboxtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingRewardSqlStoreTest {
    @TempDir
    Path tempDirectory;

    private Path database;
    private PendingRewardSqlStore store;

    @BeforeEach
    void setUp() throws Exception {
        database = tempDirectory.resolve("data.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE pending_rewards ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, "
                    + "item BLOB NOT NULL, created_at INTEGER NOT NULL, batch_id TEXT, item_index INTEGER)");
            statement.executeUpdate("CREATE TABLE pending_reward_quarantine ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, original_id INTEGER NOT NULL, "
                    + "player_uuid TEXT NOT NULL, item BLOB NOT NULL, created_at INTEGER NOT NULL, "
                    + "quarantined_at INTEGER NOT NULL, error TEXT NOT NULL)");
        }
        store = new PendingRewardSqlStore(() -> database);
    }

    @Test
    void seedAndClearOnlyTouchTaggedTestRows() throws Exception {
        UUID playerId = UUID.randomUUID();
        insertProductionRow(playerId, new byte[]{9});

        store.seed(playerId, List.of(new byte[]{1}, new byte[]{2}));
        PendingRewardCounts counts = store.inspect(playerId);
        assertEquals(3, counts.pending());
        assertEquals(2, counts.testPending());

        store.clear(playerId);
        counts = store.inspect(playerId);
        assertEquals(1, counts.pending());
        assertEquals(0, counts.testPending());
    }

    @Test
    void corruptPairContainsOneValidAndOneRecognizableCorruptRecord() throws Exception {
        UUID playerId = UUID.randomUUID();

        store.seedCorruptPair(playerId, new byte[]{1, 2, 3});

        PendingRewardCounts counts = store.inspect(playerId);
        assertEquals(2, counts.pending());
        assertEquals(2, counts.testPending());
        assertEquals(1, countCorruptPending(playerId));
    }

    @Test
    void clearKeepsUnrelatedQuarantineRowsForTheSamePlayer() throws Exception {
        UUID playerId = UUID.randomUUID();
        insertQuarantineRow(playerId, new byte[]{9});
        insertQuarantineRow(playerId, PendingRewardSqlStore.corruptPayload());

        store.clear(playerId);

        PendingRewardCounts counts = store.inspect(playerId);
        assertEquals(2, counts.quarantine());
        assertEquals(0, counts.testQuarantine());
    }

    @Test
    void clearsOnlyQuarantineRowWhoseOriginalIdWasSeededByThisStore() throws Exception {
        UUID playerId = UUID.randomUUID();
        store.seedCorruptPair(playerId, new byte[]{1, 2, 3});
        moveCorruptPendingRowToQuarantine(playerId);

        assertEquals(1, store.inspect(playerId).testQuarantine());
        store.clear(playerId);

        assertEquals(0, store.inspect(playerId).quarantine());
    }

    @Test
    void recognizesAndClearsDerivedUntaggedRowsByOwnedPayloadMarker() throws Exception {
        UUID playerId = UUID.randomUUID();
        insertProductionRow(playerId, new byte[]{9});
        insertProductionRow(playerId, new byte[]{7});

        PendingRewardCounts counts = store.inspect(playerId, payload -> payload[0] == 7);
        assertEquals(2, counts.pending());
        assertEquals(1, counts.testPending());

        store.clear(playerId, payload -> payload[0] == 7);
        counts = store.inspect(playerId, payload -> payload[0] == 7);
        assertEquals(1, counts.pending());
        assertEquals(0, counts.testPending());
    }

    private void insertProductionRow(UUID playerId, byte[] payload) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.prepareStatement(
                     "INSERT INTO pending_rewards(player_uuid,item,created_at) VALUES(?,?,?)")) {
            statement.setString(1, playerId.toString());
            statement.setBytes(2, payload);
            statement.setLong(3, 1L);
            statement.executeUpdate();
        }
    }

    private int countCorruptPending(UUID playerId) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM pending_rewards WHERE player_uuid=? AND item=?")) {
            statement.setString(1, playerId.toString());
            statement.setBytes(2, PendingRewardSqlStore.corruptPayload());
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private void insertQuarantineRow(UUID playerId, byte[] payload) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.prepareStatement(
                     "INSERT INTO pending_reward_quarantine("
                             + "original_id,player_uuid,item,created_at,quarantined_at,error) "
                             + "VALUES(?,?,?,?,?,?)")) {
            statement.setLong(1, System.nanoTime());
            statement.setString(2, playerId.toString());
            statement.setBytes(3, payload);
            statement.setLong(4, 1L);
            statement.setLong(5, 2L);
            statement.setString(6, "test");
            statement.executeUpdate();
        }
    }

    private void moveCorruptPendingRowToQuarantine(UUID playerId) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            connection.setAutoCommit(false);
            long id;
            try (var query = connection.prepareStatement(
                    "SELECT id FROM pending_rewards WHERE player_uuid=? AND item=?")) {
                query.setString(1, playerId.toString());
                query.setBytes(2, PendingRewardSqlStore.corruptPayload());
                try (var result = query.executeQuery()) {
                    result.next();
                    id = result.getLong(1);
                }
            }
            try (var insert = connection.prepareStatement(
                         "INSERT INTO pending_reward_quarantine("
                                 + "original_id,player_uuid,item,created_at,quarantined_at,error) VALUES(?,?,?,?,?,?)");
                 var delete = connection.prepareStatement("DELETE FROM pending_rewards WHERE id=?")) {
                insert.setLong(1, id);
                insert.setString(2, playerId.toString());
                insert.setBytes(3, PendingRewardSqlStore.corruptPayload());
                insert.setLong(4, 1L);
                insert.setLong(5, 2L);
                insert.setString(6, "corrupt");
                insert.executeUpdate();
                delete.setLong(1, id);
                delete.executeUpdate();
            }
            connection.commit();
        }
    }
}
