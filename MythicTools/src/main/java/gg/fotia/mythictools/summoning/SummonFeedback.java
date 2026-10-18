package gg.fotia.mythictools.summoning;

import gg.fotia.mythictools.summoning.effect.ParticleEffects;
import gg.fotia.mythictools.text.MessageRenderer;
import java.util.Map;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

final class SummonFeedback {
    private final MessageRenderer messages;
    private final ParticleEffects effects;
    SummonFeedback(MessageRenderer messages, ParticleEffects effects) { this.messages = messages; this.effects = effects; }

    void sound(SummonRitual ritual, String stage) {
        for (var cue : ritual.definition.sounds()) {
            if (!cue.stage().equals(stage) || cue.sound().isBlank()) continue;
            for (Player player : effects.viewers(ritual.origin, ritual.preview ? ritual.player : null)) {
                player.playSound(ritual.origin, cue.sound(), cue.volume(), cue.pitch());
            }
        }
    }
    void countdown(Player player, SummonRitual ritual) {
        int remaining = (ritual.definition.windupTicks() - ritual.age + 19) / 20;
        if (ritual.lastCountdown == remaining) return;
        ritual.lastCountdown = remaining;
        var component = text(player, ritual, "countdown", remaining);
        String legacy = LegacyComponentSerializer.legacySection().serialize(component);
        switch (ritual.definition.countdown()) {
            case "chat" -> messages.send(player, component);
            case "title" -> player.sendTitle(legacy, "", 0, 25, 0);
            case "actionbar" -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacy));
            default -> { }
        }
    }
    void clear(Player player, SummonRitual ritual) {
        if (player == null || !player.isOnline()) return;
        if (ritual.definition.countdown().equals("title")) player.resetTitle();
        if (ritual.definition.countdown().equals("actionbar")) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    void message(Player player, SummonRitual ritual, String stage) {
        if (player != null && player.isOnline()) messages.send(player, text(player, ritual, stage, 0));
    }
    private net.kyori.adventure.text.Component text(Player player, SummonRitual ritual, String stage, int seconds) {
        String boss = ritual.definition.bossId().isBlank() ? ritual.definition.id() : ritual.definition.bossId();
        var variables = Map.of("seconds", seconds, "boss", boss, "id", ritual.definition.id());
        String override = ritual.definition.messages().getOrDefault(stage, "");
        return override.isBlank() ? messages.renderKey(player, "summon." + stage, variables)
                : messages.render(override, player, variables);
    }
}
