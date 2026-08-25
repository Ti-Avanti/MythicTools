package gg.fotia.mythictools.platform;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

/** 隔离 Paper 原生组件接口与 Spigot 传统文本接口。 */
public interface PlatformBridge {
    void send(CommandSender sender, Component component);

    Inventory createInventory(InventoryHolder holder, int size, Component title);

    void displayName(ItemMeta meta, Component displayName);

    List<Component> lore(ItemMeta meta);

    void lore(ItemMeta meta, List<Component> lore);

    ServerPlatform platform();
}
