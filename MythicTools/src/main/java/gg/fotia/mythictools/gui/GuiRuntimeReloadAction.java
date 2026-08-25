package gg.fotia.mythictools.gui;

import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** 完整重载成功后，只通过新运行时的消息与 GUI 实例继续交互。 */
public final class GuiRuntimeReloadAction implements Consumer<Player> {
    private final Runnable reloadRuntime;
    private final Consumer<Player> notifySuccess;
    private final Consumer<Player> openCurrentGui;

    public GuiRuntimeReloadAction(
            Runnable reloadRuntime,
            Consumer<Player> notifySuccess,
            Consumer<Player> openCurrentGui) {
        this.reloadRuntime = Objects.requireNonNull(reloadRuntime, "reloadRuntime");
        this.notifySuccess = Objects.requireNonNull(notifySuccess, "notifySuccess");
        this.openCurrentGui = Objects.requireNonNull(openCurrentGui, "openCurrentGui");
    }

    @Override
    public void accept(Player player) {
        reloadRuntime.run();
        notifySuccess.accept(player);
        openCurrentGui.accept(player);
    }
}
