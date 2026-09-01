package gg.fotia.mythictools.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** 奖励二级编辑器使用的隔离草稿，取消时不会污染主配置会话。 */
final class RewardEntryDraft {
    private final String type;
    private final Map<String, Object> values;
    private ItemStack item;

    private RewardEntryDraft(String type, ItemStack item, Map<String, Object> values) {
        this.type = type;
        this.item = item == null ? null : item.clone();
        this.values = values;
    }

    static RewardEntryDraft create(String rawType, String defaultRarity) {
        String type = rawType.equalsIgnoreCase("command") ? "command" : "item";
        String rarity = defaultRarity == null ? "" : defaultRarity.trim();
        if (rarity.isEmpty()) {
            throw new IllegalArgumentException("没有可用的奖励稀有度");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("grant-mode", "weighted");
        values.put("weight", 100L);
        values.put("min-amount", 1);
        values.put("max-amount", 1);
        values.put("rarity", rarity);
        values.put("display.zh_CN", type.equals("item") ? "新物品奖励" : "新指令奖励");
        values.put("display.en_US", type.equals("item") ? "New Item Reward" : "New Command Reward");
        values.put("message.zh_CN", "<!i><green>获得 {reward} x{amount}");
        values.put("message.en_US", "<!i><green>Received {reward} x{amount}");
        if (type.equals("item")) {
            values.put("delivery", "ground");
        } else {
            values.put("command", "say Reward for {player}");
            values.put("executor", "console");
        }
        return new RewardEntryDraft(type, null, values);
    }

    static RewardEntryDraft load(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("奖励条目不存在: " + path);
        }
        String type = normalizeType(section.getString("type", "item"));
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key) || key.equals("type")
                    || key.equals("item") || key.startsWith("item.")
                    || type.equals("item") && key.equals("material")) {
                continue;
            }
            values.put(key, copyValue(section.get(key)));
        }
        values.putIfAbsent("weight", 100L);
        values.put("weight", normalizeWeight(values.get("weight")));
        values.putIfAbsent("grant-mode", "weighted");
        if (type.equals("item")) {
            values.putIfAbsent("delivery", "ground");
        } else {
            values.putIfAbsent("executor", "console");
        }
        ItemStack stored = section.getItemStack("item");
        if (stored == null && section.isString("material")) {
            Material material = Material.matchMaterial(section.getString("material", ""));
            if (material != null && material != Material.AIR) {
                stored = new ItemStack(material);
            }
        }
        return new RewardEntryDraft(type, stored, values);
    }

    String type() {
        return type;
    }

    ItemStack item() {
        return item == null ? new ItemStack(Material.AIR) : item.clone();
    }

    boolean hasItem() {
        return item != null;
    }

    void item(ItemStack value) {
        item = value == null ? null : value.clone();
        if (item != null) {
            item.setAmount(1);
        }
    }

    Object value(String path) {
        return values.get(path);
    }

    void set(String path, Object value) {
        values.put(path, path.equals("weight") ? normalizeWeight(value) : copyValue(value));
    }

    long weight() {
        Object value = values.get("weight");
        return value == null ? 1L : (Long) normalizeWeight(value);
    }

    List<String> fields() {
        boolean firstDefeat = "first-defeat".equals(String.valueOf(values.get("grant-mode")));
        return values.keySet().stream()
                .filter(field -> !firstDefeat || !field.equals("weight"))
                .sorted(Comparator.naturalOrder()).toList();
    }

    void applyTo(YamlConfiguration yaml, String path) {
        yaml.set(path, null);
        yaml.set(path + ".type", type);
        if (item != null) {
            yaml.set(path + ".item", item.clone());
            yaml.set(path + ".material", item.getType().name());
        }
        values.forEach((key, value) -> yaml.set(path + "." + key, copyValue(value)));
    }

    private static Object copyValue(Object value) {
        if (value instanceof ItemStack stack) {
            return stack.clone();
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>();
            list.forEach(entry -> copied.add(copyValue(entry)));
            return copied;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copied = new LinkedHashMap<>();
            map.forEach((key, entry) -> copied.put(key, copyValue(entry)));
            return copied;
        }
        return value;
    }

    private static Long normalizeWeight(Object value) {
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof java.math.BigInteger integer) {
            try {
                return integer.longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("权重超出 64 位整数范围", exception);
            }
        }
        throw new IllegalArgumentException("权重必须是整数");
    }

    private static String normalizeType(String rawType) {
        String type = rawType == null ? "item" : rawType.trim().toLowerCase(java.util.Locale.ROOT);
        if (!type.equals("item") && !type.equals("command")) {
            throw new IllegalArgumentException("未知奖励类型: " + rawType);
        }
        return type;
    }
}
