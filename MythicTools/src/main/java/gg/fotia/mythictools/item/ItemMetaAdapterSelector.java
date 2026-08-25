package gg.fotia.mythictools.item;

import gg.fotia.mythictools.version.ServerVersion;
import java.util.logging.Logger;

/** 按已声明的服务端能力选择物品显示元数据实现。 */
final class ItemMetaAdapterSelector {
    private ItemMetaAdapterSelector() {
    }

    static ItemMetaAdapter select(ServerVersion version, Logger logger) {
        if (version.supportsItemModelAndTooltipStyle()) {
            return new ModernItemMetaAdapter(logger);
        }
        return new LegacyItemMetaAdapter(logger);
    }
}
