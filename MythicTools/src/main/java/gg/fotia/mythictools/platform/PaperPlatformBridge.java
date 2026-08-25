package gg.fotia.mythictools.platform;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

/** 使用 Paper 原生 Adventure 组件能力，保留完整 GUI 与聊天显示。 */
public final class PaperPlatformBridge implements PlatformBridge {
    @Override
    public void send(CommandSender sender, Component component) {
        sender.sendMessage(component);
    }

    @Override
    public Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, title);
    }

    @Override
    public void displayName(ItemMeta meta, Component displayName) {
        meta.displayName(displayName);
    }

    @Override
    public List<Component> lore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        return lore == null ? List.of() : List.copyOf(lore);
    }

    @Override
    public void lore(ItemMeta meta, List<Component> lore) {
        meta.lore(lore);
    }

    @Override
    public ServerPlatform platform() {
        return ServerPlatform.PAPER;
    }
}
