package gg.fotia.mythictools.reward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** 在主线程编码已抽取的完整奖励，不依赖重载后的配置引用或旧 Player 实例。 */
final class FirstDefeatPayload {
    private FirstDefeatPayload() {
    }

    static String encode(RewardRecipient recipient, List<RewardGrant> grants, Location location,
                         Map<String, ?> variables, boolean forceInventory) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.set("player", recipient.playerId().toString());
        yaml.set("name", recipient.playerName());
        yaml.set("inventory", forceInventory);
        if (location != null && location.getWorld() != null) {
            yaml.set("world", location.getWorld().getUID().toString());
            yaml.set("x", location.getX());
            yaml.set("y", location.getY());
            yaml.set("z", location.getZ());
        }
        Map<String, String> strings = new HashMap<>();
        variables.forEach((key, value) -> strings.put(key, String.valueOf(value)));
        strings.put("player", recipient.playerName());
        yaml.createSection("variables", strings);
        for (int index = 0; index < grants.size(); index++) {
            RewardGrant grant = grants.get(index);
            RewardEntry entry = grant.entry();
            ConfigurationSection section = yaml.createSection("grants." + index);
            section.set("id", entry.id());
            section.set("type", entry.type().name());
            section.set("amount", grant.amount());
            section.set("rarity", entry.rarityId());
            section.createSection("displays", entry.displays());
            section.createSection("messages", entry.messages());
            section.set("item", entry.item());
            section.set("delivery", entry.delivery().name());
            section.set("command", entry.command());
            section.set("executor", entry.commandExecutor().name());
        }
        return yaml.saveToString();
    }

    static Restored decode(String encoded) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(encoded);
        if (yaml.getInt("version") != 1) {
            throw new IllegalArgumentException("无法识别的首次击败奖励格式");
        }
        UUID playerId = UUID.fromString(yaml.getString("player"));
        Location location = null;
        if (yaml.contains("world")) {
            var world = Bukkit.getWorld(UUID.fromString(yaml.getString("world")));
            if (world == null) {
                throw new IllegalStateException("首次击败奖励所在世界尚未加载");
            }
            location = new Location(world, yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"));
        }
        List<RewardGrant> grants = new ArrayList<>();
        ConfigurationSection root = yaml.getConfigurationSection("grants");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection entry = root.getConfigurationSection(key);
                int amount = entry.getInt("amount");
                grants.add(new RewardGrant(new RewardEntry(
                        entry.getString("id"), RewardType.valueOf(entry.getString("type")),
                        RewardGrantMode.FIRST_DEFEAT, 0L, amount, amount, entry.getString("rarity"),
                        strings(entry.getConfigurationSection("displays")),
                        strings(entry.getConfigurationSection("messages")), entry.getItemStack("item"),
                        RewardDelivery.valueOf(entry.getString("delivery")), entry.getString("command", ""),
                        CommandExecutorType.valueOf(entry.getString("executor"))), amount));
            }
        }
        return new Restored(new RewardRecipient(playerId, yaml.getString("name"), Bukkit.getPlayer(playerId)),
                List.copyOf(grants), location, strings(yaml.getConfigurationSection("variables")),
                yaml.getBoolean("inventory"));
    }

    private static Map<String, String> strings(ConfigurationSection section) {
        Map<String, String> result = new HashMap<>();
        if (section != null) {
            section.getValues(false).forEach((key, value) -> result.put(key, String.valueOf(value)));
        }
        return Map.copyOf(result);
    }

    record Restored(RewardRecipient recipient, List<RewardGrant> grants, Location location,
                    Map<String, String> variables, boolean forceInventory) {
        boolean ready() {
            if (recipient.onlinePlayer() == null && grants.stream().anyMatch(grant ->
                    grant.entry().type() == RewardType.COMMAND
                            && grant.entry().commandExecutor() == CommandExecutorType.PLAYER)) {
                return false;
            }
            return forceInventory || location == null || grants.stream().noneMatch(grant ->
                    grant.entry().type() == RewardType.ITEM && grant.entry().delivery() == RewardDelivery.GROUND)
                    || location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }
    }
}
