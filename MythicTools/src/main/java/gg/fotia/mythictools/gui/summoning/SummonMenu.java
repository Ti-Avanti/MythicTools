package gg.fotia.mythictools.gui.summoning;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class SummonMenu implements InventoryHolder {
    final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    Inventory inventory;
    public Inventory getInventory() { return inventory; }
}
