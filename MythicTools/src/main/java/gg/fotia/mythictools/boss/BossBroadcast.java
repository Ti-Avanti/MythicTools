package gg.fotia.mythictools.boss;

import java.util.List;
import java.util.Map;

/** Boss 出生或死亡的多语言播报与控制台指令。 */
public record BossBroadcast(Map<String, String> messages, List<String> commands) {
    public BossBroadcast {
        messages = Map.copyOf(messages);
        commands = List.copyOf(commands);
    }

    public String message(String locale) {
        return messages.getOrDefault(locale, messages.getOrDefault("zh_CN", ""));
    }
}
