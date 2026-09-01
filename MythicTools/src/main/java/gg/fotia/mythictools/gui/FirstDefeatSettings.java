package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.reward.RewardEntryRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/** 首次击败 GUI 对 Boss 与普通怪物 YAML 的统一读写规则。 */
final class FirstDefeatSettings {
    private FirstDefeatSettings() {
    }

    static String root(AdminType type) {
        return switch (type) {
            case BOSS -> "rewards.first-defeat";
            case MOB_DROP -> "first-defeat";
            default -> throw new IllegalArgumentException("该类型不支持首次击败奖励: " + type);
        };
    }

    static void ensureDefaults(YamlConfiguration yaml, AdminType type) {
        String root = root(type);
        if (!yaml.isSet(root + ".enabled")) {
            yaml.set(root + ".enabled", false);
        }
        if (!yaml.isSet(root + ".scope")) {
            yaml.set(root + ".scope", "player");
        }
        if (!yaml.isSet(root + ".recipient")) {
            yaml.set(root + ".recipient", "killer");
        }
        if (!yaml.isSet(root + ".entries")) {
            yaml.set(root + ".entries", List.of());
        }
    }

    static boolean enabled(YamlConfiguration yaml, AdminType type) {
        return yaml.getBoolean(root(type) + ".enabled", false);
    }

    static String scope(YamlConfiguration yaml, AdminType type) {
        return yaml.getString(root(type) + ".scope", "player");
    }

    static String recipient(YamlConfiguration yaml, AdminType type) {
        return yaml.getString(root(type) + ".recipient", "killer");
    }

    static void setEnabled(YamlConfiguration yaml, AdminType type, boolean enabled) {
        ensureDefaults(yaml, type);
        yaml.set(root(type) + ".enabled", enabled);
    }

    static void setScope(YamlConfiguration yaml, AdminType type, String scope) {
        ensureDefaults(yaml, type);
        String normalized = scope.equalsIgnoreCase("server") ? "server" : "player";
        yaml.set(root(type) + ".scope", normalized);
        if (normalized.equals("server")) {
            yaml.set(root(type) + ".recipient", "killer");
        }
    }

    static void setRecipient(YamlConfiguration yaml, AdminType type, String recipient) {
        ensureDefaults(yaml, type);
        if (type == AdminType.MOB_DROP || scope(yaml, type).equals("server")) {
            yaml.set(root(type) + ".recipient", "killer");
            return;
        }
        yaml.set(root(type) + ".recipient",
                recipient.equalsIgnoreCase("participants") ? "participants" : "killer");
    }

    static List<RewardEntryRef> entries(YamlConfiguration yaml, AdminType type) {
        List<RewardEntryRef> result = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList(root(type) + ".entries")) {
            Object group = raw.get("group");
            Object entry = raw.get("entry");
            if (group != null && entry != null) {
                result.add(new RewardEntryRef(String.valueOf(group), String.valueOf(entry)));
            }
        }
        return List.copyOf(result);
    }

    static void add(YamlConfiguration yaml, AdminType type, RewardEntryRef reference) {
        List<RewardEntryRef> values = new ArrayList<>(entries(yaml, type));
        if (!values.contains(reference)) {
            values.add(reference);
        }
        writeEntries(yaml, type, values);
    }

    static void remove(YamlConfiguration yaml, AdminType type, RewardEntryRef reference) {
        List<RewardEntryRef> values = new ArrayList<>(entries(yaml, type));
        values.remove(reference);
        writeEntries(yaml, type, values);
    }

    private static void writeEntries(
            YamlConfiguration yaml,
            AdminType type,
            List<RewardEntryRef> values) {
        List<Map<String, String>> serialized = new ArrayList<>();
        for (RewardEntryRef value : values) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("group", value.groupId());
            row.put("entry", value.entryId());
            serialized.add(row);
        }
        yaml.set(root(type) + ".entries", serialized);
    }
}
