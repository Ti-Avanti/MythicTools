package gg.fotia.mythictools.gui;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** 距离等级模块的导航入口，其配置复用通用编辑器。 */
final class LevelingGuiController {
    private final GuiContext context;

    LevelingGuiController(GuiContext context) {
        this.context = context;
    }

    void openMenu(Player player) {
        context.sessionService.resetEditingState(player.getUniqueId());
        GuiTemplate template = context.screens.template("leveling-menu");
        GuiHolder holder = new GuiHolder(GuiView.LEVELING_MENU, null, null, 0);
        Inventory inventory = context.screens.createInventory(holder, template, player, Map.of());
        for (char symbol : new char[]{'g', 'p', 'a', 'b'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleMenu(Player player, int slot) {
        GuiTemplate template = context.screens.template("leveling-menu");
        if (template.slots('g').contains(slot)) {
            context.core.openList(player, AdminType.LEVEL_GROUP, 0);
        } else if (template.slots('p').contains(slot)) {
            context.core.openList(player, AdminType.LEVEL_POINT, 0);
        } else if (template.slots('a').contains(slot)) {
            context.core.openList(player, AdminType.LEVEL_REGION, 0);
        } else if (template.slots('b').contains(slot)) {
            context.core.openMain(player);
        }
    }
}
