package gg.fotia.mythictools.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.lang.LocaleService;
import gg.fotia.mythictools.text.MessageRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.mockito.MockMakers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class MythicToolsCommandTest {

    @ParameterizedTest
    @ValueSource(strings = {"language", "lang"})
    void removedLanguageCommandsUseUnknownCommandRoute(String subCommand) {
        MythicToolsPlugin plugin = inlineMock(MythicToolsPlugin.class);
        MessageRenderer messages = inlineMock(MessageRenderer.class);
        LocaleService locales = inlineMock(LocaleService.class);
        Player player = mock(Player.class);
        when(plugin.messages()).thenReturn(messages);
        when(plugin.locales()).thenReturn(locales);
        when(locales.hasLocale("en_US")).thenReturn(true);
        MythicToolsCommand executor = new MythicToolsCommand(plugin);

        executor.onCommand(player, mock(Command.class), "mt", new String[] {subCommand, "en_US"});

        verify(messages).renderKey(player, "common.unknown-command", Map.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "l", "lang", "language"})
    void tabCompletionNeverSuggestsRemovedLanguageCommand(String prefix) {
        MythicToolsCommand executor = new MythicToolsCommand(null);

        var suggestions = executor.onTabComplete(
                mock(Player.class), mock(Command.class), "mt", new String[] {prefix});

        assertFalse(suggestions.stream().anyMatch(value -> value.equalsIgnoreCase("language")));
        assertFalse(suggestions.stream().anyMatch(value -> value.equalsIgnoreCase("lang")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"language", "lang"})
    void removedLanguageCommandsHaveNoArgumentCompletions(String subCommand) {
        MythicToolsCommand executor = new MythicToolsCommand(null);

        var suggestions = executor.onTabComplete(
                mock(Player.class), mock(Command.class), "mt", new String[] {subCommand, ""});

        assertTrue(suggestions.isEmpty());
    }

    @Test
    void commandUsageDoesNotAdvertiseRemovedLanguageCommand() throws IOException {
        String pluginYaml;
        try (var stream = MythicToolsCommandTest.class.getResourceAsStream("/plugin.yml")) {
            pluginYaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String usage = pluginYaml.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("usage:"))
                .findFirst()
                .orElseThrow();

        assertFalse(usage.toLowerCase(java.util.Locale.ROOT).contains("language"));
        assertFalse(usage.toLowerCase(java.util.Locale.ROOT).contains("lang"));
    }

    private static <T> T inlineMock(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.INLINE));
    }
}
