package gg.fotia.mythictools.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingRewardJournalTest {

    @TempDir
    Path tempDir;

    @Test
    void reopensCompleteBatchWithOrderedPayloads() throws Exception {
        Path path = tempDir.resolve("pending-rewards.bin");
        UUID playerId = UUID.randomUUID();
        JournalBatch appended;
        try (RewardJournal journal = new PendingRewardJournal(path)) {
            appended = journal.append(playerId, List.of(payload("first"), payload("second")));
        }

        try (RewardJournal reopened = new PendingRewardJournal(path)) {
            List<JournalBatch> batches = reopened.batches();

            assertEquals(1, batches.size());
            assertEquals(appended.batchId(), batches.get(0).batchId());
            assertEquals(playerId, batches.get(0).playerId());
            assertEquals(2, batches.get(0).itemPayloads().size());
            assertArrayEquals(payload("first"), batches.get(0).itemPayloads().get(0));
            assertArrayEquals(payload("second"), batches.get(0).itemPayloads().get(1));
        }
    }

    @Test
    void frameCarriesVersionIdsIndexesPayloadsAndChecksum() throws Exception {
        Path path = tempDir.resolve("format.bin");
        UUID playerId = new UUID(0x1122334455667788L, 0x0102030405060708L);
        JournalBatch batch;
        try (RewardJournal journal = new PendingRewardJournal(path)) {
            batch = journal.append(playerId, List.of(payload("zero"), payload("one")));
        }

        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer frame = ByteBuffer.wrap(bytes);
        frame.getInt();
        assertEquals(1, frame.getInt());
        assertEquals(bytes.length - 16, frame.getInt());
        assertEquals(batch.batchId(), readUuid(frame));
        assertEquals(playerId, readUuid(frame));
        assertEquals(2, frame.getInt());
        assertItem(frame, 0, payload("zero"));
        assertItem(frame, 1, payload("one"));

        CRC32 checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length - Integer.BYTES);
        assertEquals((int) checksum.getValue(), frame.getInt());
        assertEquals(bytes.length, frame.position());
    }

    @Test
    void ignoresCrashTailAndAcceptsLaterAppends() throws Exception {
        Path path = tempDir.resolve("crash-tail.bin");
        UUID firstPlayer = UUID.randomUUID();
        JournalBatch first;
        try (RewardJournal journal = new PendingRewardJournal(path)) {
            first = journal.append(firstPlayer, List.of(payload("complete")));
        }
        long completeLength = Files.size(path);
        Files.write(path, new byte[] {0x4D, 0x54, 0x52, 0x4A, 0x00}, StandardOpenOption.APPEND);

        UUID secondPlayer = UUID.randomUUID();
        try (RewardJournal recovered = new PendingRewardJournal(path)) {
            assertEquals(List.of(first), recovered.batches());
            assertEquals(completeLength, Files.size(path));
            recovered.append(secondPlayer, List.of(payload("after-recovery")));
        }

        try (RewardJournal reopened = new PendingRewardJournal(path)) {
            assertEquals(2, reopened.batches().size());
            assertEquals(firstPlayer, reopened.batches().get(0).playerId());
            assertEquals(secondPlayer, reopened.batches().get(1).playerId());
        }
    }

    @Test
    void preservesFileAndFailsClosedWhenCompleteFinalBatchChecksumDoesNotMatch() throws Exception {
        Path path = tempDir.resolve("checksum.bin");
        try (RewardJournal journal = new PendingRewardJournal(path)) {
            journal.append(UUID.randomUUID(), List.of(payload("intact")));
            journal.append(UUID.randomUUID(), List.of(payload("corrupt")));
        }
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 1] ^= 0x01;
        Files.write(path, bytes, StandardOpenOption.TRUNCATE_EXISTING);

        assertThrows(IOException.class, () -> new PendingRewardJournal(path));
        assertArrayEquals(bytes, Files.readAllBytes(path));
    }

    @Test
    void preservesFileAndFailsClosedForCorruptMiddleBatch() throws Exception {
        Path path = tempDir.resolve("corrupt-middle.bin");
        long secondBatchEnd;
        try (RewardJournal journal = new PendingRewardJournal(path)) {
            journal.append(UUID.randomUUID(), List.of(payload("first")));
            journal.append(UUID.randomUUID(), List.of(payload("middle")));
            secondBatchEnd = Files.size(path);
            journal.append(UUID.randomUUID(), List.of(payload("last")));
        }
        byte[] corrupted = Files.readAllBytes(path);
        corrupted[Math.toIntExact(secondBatchEnd) - 1] ^= 0x01;
        Files.write(path, corrupted, StandardOpenOption.TRUNCATE_EXISTING);

        assertThrows(IOException.class, () -> new PendingRewardJournal(path));
        assertArrayEquals(corrupted, Files.readAllBytes(path));
    }

    @Test
    void removeRewritesRemainingBatchesAndIsIdempotentAcrossRestart() throws Exception {
        Path path = tempDir.resolve("remove.bin");
        JournalBatch first;
        JournalBatch removed;
        JournalBatch last;
        try (RewardJournal journal = new PendingRewardJournal(path)) {
            first = journal.append(UUID.randomUUID(), List.of(payload("first")));
            removed = journal.append(UUID.randomUUID(), List.of(payload("removed")));
            last = journal.append(UUID.randomUUID(), List.of(payload("last")));
            journal.remove(removed.batchId());
        }

        try (RewardJournal reopened = new PendingRewardJournal(path)) {
            assertEquals(List.of(first, last), reopened.batches());
            reopened.remove(removed.batchId());
        }

        try (RewardJournal reopenedAgain = new PendingRewardJournal(path)) {
            assertEquals(List.of(first, last), reopenedAgain.batches());
        }
    }

    @Test
    void rejectsSecondWriterForSamePathUntilFirstCloses() throws Exception {
        Path path = tempDir.resolve("single-writer.bin");
        JournalBatch appended;
        try (RewardJournal first = new PendingRewardJournal(path)) {
            assertThrows(IOException.class, () -> new PendingRewardJournal(path));
            appended = first.append(UUID.randomUUID(), List.of(payload("owned")));
        }

        try (RewardJournal reopened = new PendingRewardJournal(path)) {
            assertEquals(List.of(appended), reopened.batches());
        }
    }

    private static byte[] payload(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static UUID readUuid(ByteBuffer source) {
        return new UUID(source.getLong(), source.getLong());
    }

    private static void assertItem(ByteBuffer source, int expectedIndex, byte[] expectedPayload) {
        assertEquals(expectedIndex, source.getInt());
        byte[] actual = new byte[source.getInt()];
        source.get(actual);
        assertArrayEquals(expectedPayload, actual);
    }
}
