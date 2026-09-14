package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.text.MessageRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** 界面构建与通用物品渲染的共享工具。 */
final class GuiScreenSupport {
    private final GuiTemplateRepository templates;
    private final MessageRenderer messages;
    private final EditorValueFormats values;

    GuiScreenSupport(GuiTemplateRepository templates, MessageRenderer messages, EditorValueFormats values) {
        this.templates = templates;
        this.messages = messages;
        this.values = values;
    }

    Inventory createInventory(GuiHolder holder, GuiTemplate template, Player player, Map<String, ?> variables) {
        Inventory inventory = messages.platform().createInventory(
                holder, template.size(), template.title(player, variables));
        holder.inventory(inventory);
        return inventory;
    }

    void fillStatic(Inventory inventory, GuiTemplate template, char symbol, Player player, Map<String, ?> variables) {
        List<Integer> slots = template.slots(symbol);
        if (slots.isEmpty()) {
            return;
        }
        ItemStack prototype = template.item(symbol, player, variables);
        for (int slot : slots) {
            inventory.setItem(slot, prototype.clone());
        }
    }

    /** 汇总模板 u/s 两类内容槽位并按位置排序。 */
    static List<Integer> selectorContentSlots(GuiTemplate template) {
        List<Integer> slots = new ArrayList<>();
        slots.addAll(template.slots('u'));
        slots.addAll(template.slots('s'));
        slots.sort(Integer::compareTo);
        return slots;
    }

    ItemStack selectorItem(
            GuiTemplate template,
            Player player,
            FieldSelectorType selectorType,
            String option,
            boolean selected) {
        ItemStack item = template.item(selected ? 's' : 'u', player, Map.of(
                "option", selectorOptionName(player, selectorType, option)));
        if (selectorType != FieldSelectorType.BIOMES) {
            return item;
        }
        var meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(messages.renderKey(player, "gui.selector.biome-translation", Map.of(
                "translation", selectorType.translatedName(option))));
        lore.addAll(messages.platform().lore(meta));
        messages.platform().lore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private String selectorOptionName(Player player, FieldSelectorType selectorType, String option) {
        String key = switch (selectorType) {
            case BOSS_PHASE_MODE -> "gui.selector.options.phase-mode." + option;
            case BOSS_INTERMEDIATE_LOOT -> "gui.selector.options.intermediate-loot." + option;
            case BOSS_FINAL_LOOT -> "gui.selector.options.final-loot." + option;
            case LEVEL_MODE -> "gui.selector.options.level-mode." + option;
            default -> null;
        };
        return key != null && messages.containsText(player, key) ? messages.text(player, key) : option;
    }

    ItemStack editorFieldItem(GuiTemplate template, Player player, String field, Object value) {
        ItemStack item = template.item('f', player, Map.of(
                "field", values.fieldLabel(player, field),
                "value", EditorValueFormats.formatValue(value)));
        if (template.usesSemanticMaterial('f')) {
            item.setType(EditorFieldIcon.forField(field, value));
        }
        return item;
    }

    String typeLabel(Player player, AdminType type) {
        return messages.text(player, "gui.types." + type.languageKey());
    }

    String categoryName(Player player, EditorCategory category) {
        return messages.text(player, "gui.category." + category.key() + ".name");
    }

    String categoryDescription(Player player, EditorCategory category) {
        return messages.text(player, "gui.category." + category.key() + ".lore");
    }

    String rewardTypeName(Player player, String type) {
        return messages.text(player, type.equalsIgnoreCase("command")
                ? "gui.reward-list.command-type" : "gui.reward-list.item-type");
    }

    GuiTemplate template(String id) {
        return templates.get(id);
    }
}
