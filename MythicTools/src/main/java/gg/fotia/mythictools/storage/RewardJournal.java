package gg.fotia.mythictools.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

interface RewardJournal extends AutoCloseable {

    JournalBatch append(UUID playerId, List<byte[]> itemPayloads) throws IOException;

    List<JournalBatch> batches();

    void remove(UUID batchId) throws IOException;

    /** 批量移除；默认实现逐个移除，具体实现可用单次重写替代。 */
    default void removeAll(java.util.Collection<UUID> batchIds) throws IOException {
        for (UUID batchId : batchIds) {
            remove(batchId);
        }
    }

    @Override
    void close() throws IOException;
}

record JournalBatch(UUID batchId, UUID playerId, List<byte[]> itemPayloads) {

    JournalBatch {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(playerId, "playerId");
        itemPayloads = copyPayloads(itemPayloads);
    }

    @Override
    public List<byte[]> itemPayloads() {
        return copyPayloads(itemPayloads);
    }

    /** 包内只读视图，供编码与入库循环使用，避免整批 payload 深拷贝。 */
    List<byte[]> payloadsView() {
        return itemPayloads;
    }

    private static List<byte[]> copyPayloads(List<byte[]> payloads) {
        Objects.requireNonNull(payloads, "itemPayloads");
        List<byte[]> copies = new ArrayList<>(payloads.size());
        for (byte[] payload : payloads) {
            copies.add(Objects.requireNonNull(payload, "itemPayload").clone());
        }
        return List.copyOf(copies);
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof JournalBatch other)
                || !batchId.equals(other.batchId)
                || !playerId.equals(other.playerId)
                || itemPayloads.size() != other.itemPayloads.size()) {
            return false;
        }
        for (int index = 0; index < itemPayloads.size(); index++) {
            if (!Arrays.equals(itemPayloads.get(index), other.itemPayloads.get(index))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(batchId, playerId);
        for (byte[] payload : itemPayloads) {
            result = 31 * result + Arrays.hashCode(payload);
        }
        return result;
    }
}
