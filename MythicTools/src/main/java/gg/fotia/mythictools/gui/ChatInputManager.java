package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.text.MessageRenderer;
import gg.fotia.mythictools.runtime.BukkitTaskScheduler;
import gg.fotia.mythictools.runtime.OwnedTasks;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 将玩家下一条聊天消息作为 GUI 文本字段输入。 */
public final class ChatInputManager implements Listener {
    private final MessageRenderer messages;
    private final ChatInputSessions sessions;
    private final OwnedTasks tasks;

    public ChatInputManager(JavaPlugin plugin, MessageRenderer messages) {
        this(plugin, messages, new ChatInputSessions(), new OwnedTasks(new BukkitTaskScheduler(plugin)));
    }

    public ChatInputManager(
            JavaPlugin plugin,
            MessageRenderer messages,
            ChatInputSessions sessions,
            OwnedTasks tasks) {
        this.messages = messages;
        this.sessions = sessions;
        this.tasks = tasks;
    }

    /** 开始等待玩家的一条聊天输入。 */
    public void begin(Player player, String field, Consumer<String> callback, Runnable cancel) {
        sessions.put(player.getUniqueId(), callback, cancel);
        player.closeInventory();
        messages.send(player, "input.prompt", Map.of("field", field));
    }

    public boolean isAwaiting(UUID playerId) {
        return sessions.isAwaiting(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatInputSessions.Session session = sessions.take(player.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage().trim();
        queueInput(player, input, session);
    }

    void queueInput(Player player, String input, ChatInputSessions.Session session) {
        UUID playerId = player.getUniqueId();
        tasks.execute(() -> {
            if (!player.isOnline() || !sessions.isCurrent(playerId, session)) {
                return;
            }
            try {
                if (input.equalsIgnoreCase("cancel")) {
                    session.cancel().run();
                    messages.send(player, "common.cancelled", Map.of());
                } else {
                    session.callback().accept(input);
                }
            } finally {
                sessions.complete(playerId, session);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        discard(event.getPlayer().getUniqueId());
    }

    public void discard(UUID playerId) {
        sessions.discard(playerId);
    }

    public void close() {
        sessions.discardAll();
        tasks.cancelAll();
    }
}
