package gg.fotia.mythictools.blackboxtest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class TestCommandRoutesTest {
    private final RecordingActions actions = new RecordingActions();
    private final TestCommand command = new TestCommand(actions);

    @Test
    void routesFixtureThroughProbeSoSpecialFixturesCanPreparePlayerAndDatabase() {
        command.dispatch(null, "fixture", new String[]{"fixture", "reward-offline"});

        assertEquals("reward-offline", actions.fixture);
    }

    @Test
    void routesFormalReloadAndExternalRemoval() {
        command.dispatch(null, "reload", new String[]{"reload"});
        command.dispatch(null, "remove", new String[]{"remove", "boss"});

        assertEquals(1, actions.reloads);
        assertEquals("boss", actions.removalTarget);
    }

    @Test
    void passesPendingSubcommandArgumentsWithoutLosingOptionalValues() {
        command.dispatch(null, "pending", new String[]{"pending", "seed", "DIAMOND", "3"});

        assertArrayEquals(new String[]{"seed", "DIAMOND", "3"}, actions.pendingArguments);
    }

    @Test
    void routesMachineAssertion() {
        command.dispatch(null, "assert", new String[]{"assert", "rollback"});

        assertEquals("rollback", actions.assertion);
    }

    @Test
    void routesAsyncPlaceholderProbe() {
        command.dispatch(null, "papi-async", new String[]{"papi-async", "language"});

        assertEquals("language", actions.asyncPlaceholder);
    }

    private static final class RecordingActions extends TestActions {
        private String fixture;
        private int reloads;
        private String removalTarget;
        private String[] pendingArguments;
        private String assertion;
        private String asyncPlaceholder;

        @Override FixtureManager fixtures() { return null; }
        @Override void snapshot(Player player) { }
        @Override void reset(Player player) { }
        @Override void restore(Player player) { }
        @Override void fixture(Player player, String mode) { fixture = mode; }
        @Override void spawn(Player player, String mobId) { }
        @Override void kill(Player player, String attribution, String mobId) { }
        @Override void damage(Player player, double amount) { }
        @Override void triggerPoint(Player player) { }
        @Override void spawnBoss(Player player) { }
        @Override void scheduleKill(Player player, int ticks) { }
        @Override void state(Player player) { }
        @Override void reload(Player player) { reloads++; }
        @Override void remove(Player player, String target) { removalTarget = target; }
        @Override void pending(Player player, String[] args) { pendingArguments = args; }
        @Override void assertCase(Player player, String testCase) { assertion = testCase; }
        @Override void placeholder(Player player, String params) { }
        @Override void asyncPlaceholder(Player player, String params) { asyncPlaceholder = params; }
        @Override void render(Player player, String fixture) { }
        @Override void visualMetadata(Player player) { }
        @Override void captureGuiVisualMetadata(Player player) { }
        @Override void mark(CommandSender sender, String key) { }
        @Override void permission(Player player, String node, String rawValue) { }
    }
}
