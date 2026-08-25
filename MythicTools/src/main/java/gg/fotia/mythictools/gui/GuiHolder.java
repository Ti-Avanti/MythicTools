package gg.fotia.mythictools.gui;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** 保存一个管理菜单的运行时导航上下文。 */
public final class GuiHolder implements InventoryHolder {
    final GuiView view;
    final AdminType type;
    final String id;
    final String context;
    final int page;
    final Map<Integer, String> valuesBySlot = new HashMap<>();
    private Inventory inventory;

    public GuiHolder(GuiView view, AdminType type, String id, int page) {
        this(view, type, id, null, page);
    }

    public GuiHolder(GuiView view, AdminType type, String id, String context, int page) {
        this.view = view;
        this.type = type;
        this.id = id;
        this.context = context;
        this.page = page;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
