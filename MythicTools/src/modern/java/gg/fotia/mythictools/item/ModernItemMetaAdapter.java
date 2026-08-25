package gg.fotia.mythictools.item;

import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

/** 使用 1.21.2 起 Bukkit/Spigot API 的物品显示元数据实现。 */
public final class ModernItemMetaAdapter implements ItemMetaAdapter {
    private final Logger logger;

    public ModernItemMetaAdapter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void apply(ItemMeta meta, ItemVisualConfig config) {
        if (config.customModelData() != null) {
            meta.setCustomModelData(config.customModelData());
        }
        applyItemModel(meta, config.itemModel());
        applyTooltipStyle(meta, config.tooltipStyle());
    }

    private void applyItemModel(ItemMeta meta, String configuredKey) {
        NamespacedKey key = parseKey(configuredKey, "item-model");
        if (key != null) {
            meta.setItemModel(key);
        }
    }

    private void applyTooltipStyle(ItemMeta meta, String configuredKey) {
        NamespacedKey key = parseKey(configuredKey, "tooltip-style");
        if (key != null) {
            meta.setTooltipStyle(key);
        }
    }

    private NamespacedKey parseKey(String configuredKey, String field) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return null;
        }
        int separator = configuredKey.indexOf(':');
        if (separator <= 0 || separator != configuredKey.lastIndexOf(':') || separator == configuredKey.length() - 1) {
            logger.warning("无效的 " + field + " 命名空间键，已忽略: " + configuredKey);
            return null;
        }
        try {
            return new NamespacedKey(
                    configuredKey.substring(0, separator),
                    configuredKey.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            logger.warning("无效的 " + field + " 命名空间键，已忽略: " + configuredKey);
            return null;
        }
    }
}
