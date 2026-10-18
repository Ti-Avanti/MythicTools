package gg.fotia.mythictools.summoning;

import gg.fotia.mythictools.summoning.config.SummonDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** 只认可本插件写入的身份标记，不以显示名称或 Lore 判断。 */
public final class SummonItems {
    private final NamespacedKey key;
    public SummonItems(Plugin plugin) { key = new NamespacedKey(plugin, "summon_id"); }

    public ItemStack create(SummonDefinition definition, int amount) {
        ItemStack stack = definition.item();
        var meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, definition.id());
        stack.setItemMeta(meta);
        stack.setAmount(amount);
        return stack;
    }

    public String identify(ItemStack stack) {
        return stack == null || !stack.hasItemMeta() ? null
                : stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
