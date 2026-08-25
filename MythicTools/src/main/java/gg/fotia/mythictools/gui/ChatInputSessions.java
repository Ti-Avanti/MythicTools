package gg.fotia.mythictools.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** 线程安全的一次性聊天输入会话存储。 */
public final class ChatInputSessions {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> activeGenerations = new HashMap<>();
    private long nextGeneration;

    public synchronized void put(UUID playerId, Consumer<String> callback, Runnable cancel) {
        long generation = ++nextGeneration;
        sessions.put(playerId, new Session(callback, cancel, generation));
        activeGenerations.put(playerId, generation);
    }

    public synchronized Optional<Session> take(UUID playerId) {
        return Optional.ofNullable(sessions.remove(playerId));
    }

    public synchronized boolean isAwaiting(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /** 排队后的回调仅在令牌仍属于该玩家当前会话时有效。 */
    public synchronized boolean isCurrent(UUID playerId, Session session) {
        return session != null && Long.valueOf(session.generation()).equals(activeGenerations.get(playerId));
    }

    /** 完成旧回调时不能误删其后创建的新会话。 */
    public synchronized void complete(UUID playerId, Session session) {
        if (isCurrent(playerId, session)) {
            activeGenerations.remove(playerId);
        }
    }

    public synchronized void discard(UUID playerId) {
        sessions.remove(playerId);
        activeGenerations.remove(playerId);
    }

    public synchronized void discardAll() {
        sessions.clear();
        activeGenerations.clear();
    }

    public record Session(Consumer<String> callback, Runnable cancel, long generation) {
    }
}
