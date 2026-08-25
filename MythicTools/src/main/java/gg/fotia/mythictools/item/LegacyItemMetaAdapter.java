package gg.fotia.mythictools.item;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.inventory.meta.ItemMeta;

/** 1.20.1 与 1.20.4 的安全物品元数据实现。 */
public final class LegacyItemMetaAdapter implements ItemMetaAdapter {
    private final Logger logger;
    private final AtomicBoolean warned = new AtomicBoolean();

    public LegacyItemMetaAdapter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void apply(ItemMeta meta, ItemVisualConfig config) {
        if (config.customModelData() != null) {
            meta.setCustomModelData(config.customModelData());
        }
        if ((config.itemModel() != null || config.tooltipStyle() != null)
                && warned.compareAndSet(false, true)) {
            logger.warning("当前服务端版本不支持 item-model/tooltip-style，已安全忽略这两个 GUI 字段");
        }
    }
}
