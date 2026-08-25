package gg.fotia.mythictools.platform;

import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

/** 将 Adventure 组件转换为 Spigot 可安全接收的组件与旧颜色文本。 */
public final class SpigotPlatformBridge implements PlatformBridge {
    private final SpigotComponentCodec codec = new SpigotComponentCodec();
    private final Logger logger;

    public SpigotPlatformBridge(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void send(CommandSender sender, Component component) {
        if (sender instanceof Player player) {
            try {
                player.spigot().sendMessage(ComponentSerializer.parse(codec.json(component)));
                return;
            } catch (RuntimeException exception) {
                logger.warning("Spigot 富文本发送失败，已降级为旧颜色文本: " + exception.getMessage());
            }
        }
        sender.sendMessage(codec.legacy(component));
    }

    @Override
    public Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, codec.legacy(title));
    }

    @Override
    public void displayName(ItemMeta meta, Component displayName) {
        meta.setDisplayName(codec.legacy(displayName));
    }

    @Override
    public List<Component> lore(ItemMeta meta) {
        List<String> lore = meta.getLore();
        return lore == null ? List.of() : lore.stream().map(codec::fromLegacy).toList();
    }

    @Override
    public void lore(ItemMeta meta, List<Component> lore) {
        meta.setLore(lore.stream().map(codec::legacy).toList());
    }

    @Override
    public ServerPlatform platform() {
        return ServerPlatform.SPIGOT;
    }
}
