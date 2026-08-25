package gg.fotia.mythictools.item;

import org.bukkit.inventory.meta.ItemMeta;

/** 不同服务端版本的物品显示元数据接缝。 */
public interface ItemMetaAdapter {
    /** 将当前版本支持的显示字段写入 ItemMeta。 */
    void apply(ItemMeta meta, ItemVisualConfig config);
}
