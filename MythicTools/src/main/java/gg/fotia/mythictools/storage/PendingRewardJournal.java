package gg.fotia.mythictools.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32;

final class PendingRewardJournal implements RewardJournal {
    private static final int MAGIC = 0x4D54524A;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTES = Integer.BYTES * 3;
    private static final int CHECKSUM_BYTES = Integer.BYTES;
    private static final int FIXED_BODY_BYTES = Long.BYTES * 4 + Integer.BYTES;
    private static final int ITEM_HEADER_BYTES = Integer.BYTES * 2;
    private static final int MAX_BODY_BYTES = 64 * 1024 * 1024;

    private final Object lock = new Object();
    private final Path path;
    private final List<JournalBatch> batches = new ArrayList<>();
    private final FileChannel lockChannel;
    private final FileLock fileLock;

    private FileChannel channel;
    private boolean closed;

    PendingRewardJournal(Path path) throws IOException {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        Path parent = this.path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        LockHandle lockHandle = acquireLock(this.path);
        lockChannel = lockHandle.channel();
        fileLock = lockHandle.lock();
        FileChannel opened = null;
        try {
            opened = openChannel(this.path);
            ReadResult result = readCompleteBatches(opened);
            batches.addAll(result.batches());
            if (result.validBytes() < opened.size()) {
                opened.truncate(result.validBytes());
                opened.force(true);
            }
            opened.position(result.validBytes());
            channel = opened;
        } catch (IOException | RuntimeException | Error exception) {
            if (opened != null) {
                try {
                    opened.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
            }
            releaseLockAfterConstructionFailure(exception);
            throw exception;
        }
    }

    @Override
    public JournalBatch append(UUID playerId, List<byte[]> itemPayloads) throws IOException {
        JournalBatch batch = new JournalBatch(UUID.randomUUID(), playerId, itemPayloads);
        byte[] encoded = encode(batch);
        synchronized (lock) {
            ensureOpen();
            long appendPosition = channel.size();
            channel.position(appendPosition);
            try {
                writeFully(channel, ByteBuffer.wrap(encoded));
                channel.force(true);
            } catch (IOException exception) {
                try {
                    channel.truncate(appendPosition);
                    channel.position(appendPosition);
                    channel.force(true);
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
            batches.add(batch);
            return batch;
        }
    }

    @Override
    public List<JournalBatch> batches() {
        synchronized (lock) {
            return List.copyOf(batches);
        }
    }

    @Override
    public void remove(UUID batchId) throws IOException {
        Objects.requireNonNull(batchId, "batchId");
        removeAll(java.util.Set.of(batchId));
    }

    /** 单次重写移除全部指定批次，供启动重放压缩使用。 */
    @Override
    public void removeAll(java.util.Collection<UUID> batchIds) throws IOException {
        Objects.requireNonNull(batchIds, "batchIds");
        if (batchIds.isEmpty()) {
            return;
        }
        java.util.Set<UUID> removals = java.util.Set.copyOf(batchIds);
        synchronized (lock) {
            ensureOpen();
            List<JournalBatch> remaining = batches.stream()
                    .filter(batch -> !removals.contains(batch.batchId()))
                    .toList();
            if (remaining.size() == batches.size()) {
                return;
            }
            rewrite(remaining);
            batches.clear();
            batches.addAll(remaining);
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            IOException failure = null;
            try {
                channel.close();
            } catch (IOException exception) {
                failure = exception;
            }
            try {
                fileLock.release();
            } catch (IOException exception) {
                failure = merge(failure, exception);
            }
            try {
                lockChannel.close();
            } catch (IOException exception) {
                failure = merge(failure, exception);
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private void rewrite(List<JournalBatch> remaining) throws IOException {
        Path tempPath = path.resolveSibling(path.getFileName() + ".rewrite.tmp");
        Files.deleteIfExists(tempPath);
        try {
            try (FileChannel temp = FileChannel.open(
                    tempPath,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                for (JournalBatch batch : remaining) {
                    writeFully(temp, ByteBuffer.wrap(encode(batch)));
                }
                temp.force(true);
            }

            channel.close();
            try {
                Files.move(
                        tempPath,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                reopenAfterFailedMove(exception);
                throw exception;
            } catch (IOException exception) {
                reopenAfterFailedMove(exception);
                throw exception;
            }
            channel = openChannel(path);
            channel.position(channel.size());
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private void reopenAfterFailedMove(IOException failure) {
        try {
            channel = openChannel(path);
            channel.position(channel.size());
        } catch (IOException reopenException) {
            failure.addSuppressed(reopenException);
        }
    }

    private void ensureOpen() throws IOException {
        if (closed || !channel.isOpen()) {
            throw new IOException("Pending reward journal is closed");
        }
    }

    private static byte[] encode(JournalBatch batch) throws IOException {
        List<byte[]> payloads = batch.payloadsView();
        long bodyLength = FIXED_BODY_BYTES;
        for (byte[] payload : payloads) {
            bodyLength += ITEM_HEADER_BYTES + (long) payload.length;
        }
        if (bodyLength > MAX_BODY_BYTES) {
            throw new IOException("Pending reward journal batch is too large: " + bodyLength);
        }

        int bodyBytes = (int) bodyLength;
        ByteBuffer frame = ByteBuffer.allocate(HEADER_BYTES + bodyBytes + CHECKSUM_BYTES);
        frame.putInt(MAGIC);
        frame.putInt(FORMAT_VERSION);
        frame.putInt(bodyBytes);
        putUuid(frame, batch.batchId());
        putUuid(frame, batch.playerId());
        frame.putInt(payloads.size());
        for (int index = 0; index < payloads.size(); index++) {
            byte[] payload = payloads.get(index);
            frame.putInt(index);
            frame.putInt(payload.length);
            frame.put(payload);
        }

        CRC32 checksum = new CRC32();
        checksum.update(frame.array(), 0, HEADER_BYTES + bodyBytes);
        frame.putInt((int) checksum.getValue());
        return frame.array();
    }

    private static ReadResult readCompleteBatches(FileChannel source) throws IOException {
        List<JournalBatch> loaded = new ArrayList<>();
        long size = source.size();
        long position = 0L;
        while (size - position >= HEADER_BYTES) {
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
            readFully(source, header, position);
            header.flip();
            int magic = header.getInt();
            int version = header.getInt();
            int bodyLength = header.getInt();
            if (magic != MAGIC) {
                if (remainingBytesAreZero(source, position, size)) {
                    // 断电时文件长度元数据可能先于数据落盘，留下零填充尾巴；按崩溃截断处理。
                    break;
                }
                throw new IOException("Invalid pending reward journal frame magic at byte " + position);
            }
            if (bodyLength < FIXED_BODY_BYTES || bodyLength > MAX_BODY_BYTES) {
                if (remainingBytesAreZero(source, position + Integer.BYTES, size)) {
                    // 仅写入了 magic 的半截帧头，其余为零填充；同样按崩溃截断处理。
                    break;
                }
                throw new IOException("Invalid pending reward journal frame length at byte " + position);
            }

            long frameLength = HEADER_BYTES + (long) bodyLength + CHECKSUM_BYTES;
            if (size - position < frameLength) {
                break;
            }
            ByteBuffer frame = ByteBuffer.allocate((int) frameLength);
            readFully(source, frame, position);
            byte[] frameBytes = frame.array();
            int storedChecksum = ByteBuffer.wrap(frameBytes, HEADER_BYTES + bodyLength, CHECKSUM_BYTES).getInt();
            CRC32 checksum = new CRC32();
            checksum.update(frameBytes, 0, HEADER_BYTES + bodyLength);
            if ((int) checksum.getValue() != storedChecksum) {
                throw new IOException("Checksum mismatch in pending reward journal at byte " + position);
            }
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported pending reward journal format version: " + version);
            }

            ByteBuffer body = ByteBuffer.wrap(frameBytes, HEADER_BYTES, bodyLength).slice();
            JournalBatch batch = decodeBody(body);
            loaded.add(batch);
            position += frameLength;
        }
        return new ReadResult(List.copyOf(loaded), position);
    }

    private static JournalBatch decodeBody(ByteBuffer body) throws IOException {
        UUID batchId = getUuid(body);
        UUID playerId = getUuid(body);
        int itemCount = body.getInt();
        if (itemCount < 0 || itemCount > body.remaining() / ITEM_HEADER_BYTES) {
            throw new IOException("Invalid pending reward journal item count: " + itemCount);
        }
        List<byte[]> payloads = new ArrayList<>(itemCount);
        for (int expectedIndex = 0; expectedIndex < itemCount; expectedIndex++) {
            if (body.remaining() < ITEM_HEADER_BYTES) {
                throw new IOException("Truncated pending reward journal item header");
            }
            int itemIndex = body.getInt();
            int payloadLength = body.getInt();
            if (itemIndex != expectedIndex || payloadLength < 0 || payloadLength > body.remaining()) {
                throw new IOException("Invalid pending reward journal item entry: " + itemIndex);
            }
            byte[] payload = new byte[payloadLength];
            body.get(payload);
            payloads.add(payload);
        }
        if (body.hasRemaining()) {
            throw new IOException("Unexpected bytes in pending reward journal batch");
        }
        return new JournalBatch(batchId, playerId, payloads);
    }

    private static void putUuid(ByteBuffer target, UUID uuid) {
        target.putLong(uuid.getMostSignificantBits());
        target.putLong(uuid.getLeastSignificantBits());
    }

    private static UUID getUuid(ByteBuffer source) throws IOException {
        if (source.remaining() < Long.BYTES * 2) {
            throw new IOException("Truncated UUID in pending reward journal");
        }
        return new UUID(source.getLong(), source.getLong());
    }

    private static FileChannel openChannel(Path path) throws IOException {
        return FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
    }

    private static LockHandle acquireLock(Path journalPath) throws IOException {
        Path lockPath = journalPath.resolveSibling(journalPath.getFileName() + ".lock");
        FileChannel lockChannel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try {
                lock = lockChannel.tryLock();
            } catch (OverlappingFileLockException exception) {
                throw new IOException("Pending reward journal is already open: " + journalPath, exception);
            }
            if (lock == null) {
                throw new IOException("Pending reward journal is already open: " + journalPath);
            }
            return new LockHandle(lockChannel, lock);
        } catch (IOException | RuntimeException | Error exception) {
            try {
                lockChannel.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private void releaseLockAfterConstructionFailure(Throwable failure) {
        try {
            fileLock.release();
        } catch (IOException releaseException) {
            failure.addSuppressed(releaseException);
        }
        try {
            lockChannel.close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    private static IOException merge(IOException current, IOException additional) {
        if (current == null) {
            return additional;
        }
        current.addSuppressed(additional);
        return current;
    }

    private static void writeFully(FileChannel target, ByteBuffer source) throws IOException {
        while (source.hasRemaining()) {
            target.write(source);
        }
    }

    private static boolean remainingBytesAreZero(FileChannel source, long from, long size) throws IOException {
        ByteBuffer chunk = ByteBuffer.allocate(8192);
        long offset = from;
        while (offset < size) {
            chunk.clear();
            chunk.limit((int) Math.min(chunk.capacity(), size - offset));
            readFully(source, chunk, offset);
            chunk.flip();
            while (chunk.hasRemaining()) {
                if (chunk.get() != 0) {
                    return false;
                }
            }
            offset += chunk.limit();
        }
        return true;
    }

    private static void readFully(FileChannel source, ByteBuffer target, long position) throws IOException {
        long offset = position;
        while (target.hasRemaining()) {
            int read = source.read(target, offset);
            if (read < 0) {
                throw new IOException("Unexpected end of pending reward journal");
            }
            offset += read;
        }
    }

    private record ReadResult(List<JournalBatch> batches, long validBytes) {
    }

    private record LockHandle(FileChannel channel, FileLock lock) {
    }
}
