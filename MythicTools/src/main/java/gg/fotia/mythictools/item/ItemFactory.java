package gg.fotia.mythictools.item;

import gg.fotia.mythictools.text.MessageRenderer;
import gg.fotia.mythictools.version.ServerVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** 从配置创建跨版本 GUI 物品。 */
public final class ItemFactory {
    private final MessageRenderer messages;
    private final ItemMetaAdapter metaAdapter;

    public ItemFactory(MessageRenderer messages, ServerVersion version, java.util.logging.Logger logger) {
        this.messages = messages;
        this.metaAdapter = ItemMetaAdapterSelector.select(version, logger);
    }

    /** 从 GUI 配置节点构建物品。 */
    public ItemStack create(ConfigurationSection section, Player player, Map<String, ?> variables) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("无效物品材质: " + section.getString("material"));
        }
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(99, section.getInt("amount", 1))));
        ItemMeta meta = item.getItemMeta();
        String nameKey = section.getString("display-name-key");
        String name = section.getString("display-name");
        if (nameKey != null) {
            messages.platform().displayName(meta, messages.renderKey(player, nameKey, variables));
        } else if (name != null) {
            messages.platform().displayName(meta, messages.render(name, player, variables));
        }
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String key : section.getStringList("lore-keys")) {
            lore.addAll(messages.renderKeyLines(player, key, variables));
        }
        for (String line : section.getStringList("lore")) {
            for (String part : line.split("\\n", -1)) {
                lore.add(messages.render(part, player, variables));
            }
        }
        if (!lore.isEmpty()) {
            messages.platform().lore(meta, lore);
        }
        meta.setUnbreakable(section.getBoolean("unbreakable", false));
        for (String flag : section.getStringList("item-flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // 配置加载器会记录其他字段错误；未知 flag 不阻止菜单打开。
            }
        }
        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String key : enchantments.getKeys(false)) {
                org.bukkit.NamespacedKey namespacedKey = org.bukkit.NamespacedKey.fromString(key);
                Enchantment enchantment = namespacedKey == null ? null : Enchantment.getByKey(namespacedKey);
                if (enchantment != null) {
                    meta.addEnchant(enchantment, enchantments.getInt(key, 1), true);
                }
            }
        }
        metaAdapter.apply(meta, new ItemVisualConfig(
                section.contains("custom-model-data") ? section.getInt("custom-model-data") : null,
                section.getString("item-model"),
                section.getString("tooltip-style")));
        item.setItemMeta(meta);
        return item;
    }
}
