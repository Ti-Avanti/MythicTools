package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import gg.fotia.mythictools.runtime.ManagedRuntime;
import gg.fotia.mythictools.runtime.RuntimeSwap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class GuiRuntimeReloadActionTest {
    @Test
    void successUsesTheNewRuntimeMessagesAndGui() {
        RuntimeSwap<FakeRuntime> runtimes = new RuntimeSwap<>(ignored -> { });
        FakeRuntime oldRuntime = new FakeRuntime();
        FakeRuntime newRuntime = new FakeRuntime();
        runtimes.installInitial(oldRuntime);

        Player player = mock(Player.class);
        AtomicReference<String> currentName = new AtomicReference<>("old");
        List<String> uiEvents = new ArrayList<>();

        GuiRuntimeReloadAction action = new GuiRuntimeReloadAction(
                () -> {
                    runtimes.replace(() -> newRuntime);
                    currentName.set("new");
                },
                ignored -> uiEvents.add("messages." + currentName.get()),
                ignored -> uiEvents.add("gui." + currentName.get()));

        action.accept(player);

        assertSame(newRuntime, runtimes.current());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("messages.new", "gui.new"), uiEvents);
    }

    @Test
    void preparationFailureKeepsOldRuntimeAndDoesNotRunSuccessUi() {
        RuntimeSwap<FakeRuntime> runtimes = new RuntimeSwap<>(ignored -> { });
        FakeRuntime oldRuntime = new FakeRuntime();
        runtimes.installInitial(oldRuntime);

        Player player = mock(Player.class);
        List<String> uiEvents = new ArrayList<>();
        GuiRuntimeReloadAction action = new GuiRuntimeReloadAction(
                () -> runtimes.replace(() -> {
                    throw new IllegalStateException("默认语言文件不存在");
                }),
                ignored -> uiEvents.add("messages"),
                ignored -> uiEvents.add("gui"));

        assertThrows(IllegalStateException.class, () -> action.accept(player));

        assertSame(oldRuntime, runtimes.current());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), uiEvents);
    }

    private static final class FakeRuntime implements ManagedRuntime {
        @Override
        public void activate() {
        }

        @Override
        public void close() {
        }
    }
}
