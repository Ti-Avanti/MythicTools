package gg.fotia.mythictools.reward;

import java.util.UUID;
import org.bukkit.entity.Player;

/** 一名奖励接收者的稳定身份与当前在线上下文。 */
public record RewardRecipient(UUID playerId, String playerName, Player onlinePlayer) {
    public RewardRecipient {
        if (playerId == null) {
            throw new IllegalArgumentException("奖励接收者 UUID 不能为空");
        }
        playerName = playerName == null ? playerId.toString() : playerName;
    }
}
