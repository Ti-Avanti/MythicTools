package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.item.ItemFactory;
import gg.fotia.mythictools.text.MessageRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 已通过加载期校验的字符布局 GUI 模板。 */
public final class GuiTemplate {
    private static final Map<String, String> REQUIRED_SYMBOLS = Map.ofEntries(
            Map.entry("main", "dsbrc"),
            Map.entry("drops-menu", "gmr"),
            Map.entry("spawning-menu", "bpgr"),
            Map.entry("list", "epnbc"),
            Map.entry("category", "gb"),
            Map.entry("editor", "fibs"),
            Map.entry("section-editor", "fbs"),
            Map.entry("reward-list", "eicb"),
            Map.entry("item-reward-editor", "fibs"),
            Map.entry("command-reward-editor", "fbs"),
            Map.entry("reward-rarity-selector", "usbn"),
            Map.entry("reward-option-selector", "gicpb"),
            Map.entry("reward-grant-mode-selector", "wfb"),
            Map.entry("first-defeat-menu", "esrlb"),
            Map.entry("first-defeat-reward-list", "eapbn"),
            Map.entry("first-defeat-reward-selector", "epbn"),
            Map.entry("mob-member-list", "ecb"),
            Map.entry("mob-member-editor", "mwbs"),
            Map.entry("mob-drop-group-list", "epnbc"),
            Map.entry("mob-drop-group-editor", "gwmxbs"),
            Map.entry("boss-phase-list", "ecb"),
            Map.entry("boss-phase-editor", "mludbs"),
            Map.entry("boss-ranking-reward-menu", "emcrb"),
            Map.entry("boss-reward-rank-list", "epnbc"),
            Map.entry("boss-reward-group-list", "etpnbc"),
            Map.entry("boss-reward-group-editor", "gcbs"),
            Map.entry("boss-point-schedule-list", "epbn"),
            Map.entry("boss-time-window-list", "epnbczf"),
            Map.entry("boss-time-window-editor", "aeibs"),
            Map.entry("boss-spawner-list", "epnbc"),
            Map.entry("boss-spawner-editor", "fpbsn"),
            Map.entry("selector", "usbn"),
            Map.entry("confirm", "yn"));
    private final String id;
    private final String titleKey;
    private final int size;
    private final Map<Character, List<Integer>> slots;
    private final ConfigurationSection items;
    private final MessageRenderer messages;
    private final ItemFactory itemFactory;

    public GuiTemplate(
            String id,
            YamlConfiguration yaml,
            MessageRenderer messages,
            ItemFactory itemFactory) {
        this.id = id;
        this.titleKey = requireString(yaml, "title-key");
        this.messages = messages;
        this.itemFactory = itemFactory;
        List<String> layout = yaml.getStringList("Layout");
        if (layout.isEmpty() || layout.size() > 6) {
            throw new IllegalArgumentException(id + ": Layout 行数必须在 1 到 6 之间");
        }
        Map<Character, List<Integer>> parsedSlots = new HashMap<>();
        for (int row = 0; row < layout.size(); row++) {
            String line = layout.get(row);
            if (line.length() != 9) {
                throw new IllegalArgumentException(id + ": Layout 第 " + (row + 1) + " 行长度必须为 9");
            }
            for (int column = 0; column < 9; column++) {
                char symbol = line.charAt(column);
                if (symbol != '#') {
                    parsedSlots.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(row * 9 + column);
                }
            }
        }
        String required = REQUIRED_SYMBOLS.getOrDefault(id, "");
        for (char symbol : required.toCharArray()) {
            if (parsedSlots.getOrDefault(symbol, List.of()).isEmpty()) {
                throw new IllegalArgumentException(id + ": Layout 缺少必需字符 " + symbol);
            }
        }
        this.items = yaml.getConfigurationSection("items");
        if (items == null) {
            throw new IllegalArgumentException(id + ": 缺少 items 节点");
        }
        Set<String> configured = new HashSet<>(items.getKeys(false));
        Set<String> used = new HashSet<>();
        for (char symbol : parsedSlots.keySet()) {
            String key = String.valueOf(symbol);
            used.add(key);
            if (!items.isConfigurationSection(key)) {
                throw new IllegalArgumentException(id + ": Layout 字符 " + symbol + " 没有物品定义");
            }
        }
        configured.removeAll(used);
        if (!configured.isEmpty()) {
            throw new IllegalArgumentException(id + ": 存在未被 Layout 使用的物品: " + configured);
        }
        this.size = layout.size() * 9;
        Map<Character, List<Integer>> immutable = new HashMap<>();
        parsedSlots.forEach((symbol, values) -> immutable.put(symbol, List.copyOf(values)));
        this.slots = Map.copyOf(immutable);
    }

    public int size() {
        return size;
    }

    public List<Integer> slots(char symbol) {
        return slots.getOrDefault(symbol, List.of());
    }

    /** 是否允许运行时按字段或分类语义替换模板配置的基础材质。 */
    public boolean usesSemanticMaterial(char symbol) {
        ConfigurationSection section = items.getConfigurationSection(String.valueOf(symbol));
        return section != null && section.getBoolean("semantic-material", false);
    }

    public net.kyori.adventure.text.Component title(Player player, Map<String, ?> variables) {
        return messages.renderKey(player, titleKey, variables);
    }

    public ItemStack item(char symbol, Player player, Map<String, ?> variables) {
        ConfigurationSection section = items.getConfigurationSection(String.valueOf(symbol));
        if (section == null) {
            throw new IllegalArgumentException(id + ": 未定义物品字符 " + symbol);
        }
        return itemFactory.create(section, player, variables);
    }

    private static String requireString(ConfigurationSection section, String path) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少字段 " + path);
        }
        return value;
    }
}
