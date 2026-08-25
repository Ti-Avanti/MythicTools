package gg.fotia.mythictools.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Boss 奖励配置的路径解析、数值校验与本地化显示。 */
final class BossRewardRules {
    private final GuiContext context;

    BossRewardRules(GuiContext context) {
        this.context = context;
    }

    void validateSelections(Player player, YamlConfiguration yaml) {
        List<Integer> killer = copies(player, yaml, "rewards.killer.groups");
        requireTotal(player, killer);
        List<List<Integer>> ranks = new ArrayList<>();
        for (int rank : rankIds(yaml)) {
            List<Integer> copies = copies(player, yaml, groupsPath("rank:" + rank));
            requireTotal(player, copies);
            ranks.add(copies);
        }
        try {
            BossRewardSelectionRules.requireCombined(
                    yaml.getBoolean("rewards.damage-ranking.enabled", true), ranks,
                    yaml.getBoolean("rewards.killer.enabled", true), killer,
                    context.safetyLimits.maxBossSelectionsPerRecipient());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalArgumentException(context.messages.text(
                    player, "gui.boss-reward-group-editor.combined-limit")
                    .replace("{max}", Integer.toString(
                            context.safetyLimits.maxBossSelectionsPerRecipient())));
        }
    }

    List<Integer> copies(Player player, YamlConfiguration yaml, String path) {
        List<Integer> copies = new ArrayList<>();
        List<String> loaded = context.loadedDropGroupIds.get();
        for (Map<?, ?> raw : yaml.getMapList(path)) {
            String groupId = String.valueOf(raw.get("group"));
            if (!loaded.contains(groupId)) {
                throw new IllegalArgumentException(context.messages.text(
                        player, "gui.boss-reward-group-editor.missing-group")
                        .replace("{id}", groupId));
            }
            int value = copiesValue(raw.get("copies"));
            try {
                BossRewardSelectionRules.requireCopies(value, context.safetyLimits.maxBossGroupCopies());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(context.messages.text(
                        player, "gui.boss-reward-group-editor.invalid-copies")
                        .replace("{max}", Integer.toString(context.safetyLimits.maxBossGroupCopies())));
            }
            copies.add(value);
        }
        return copies;
    }

    void requireTotal(Player player, List<Integer> copies) {
        try {
            BossRewardSelectionRules.requireTotal(copies, context.safetyLimits.maxBossSelectionsPerRecipient());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalArgumentException(context.messages.text(
                    player, "gui.boss-reward-group-editor.total-limit")
                    .replace("{max}", Integer.toString(
                            context.safetyLimits.maxBossSelectionsPerRecipient())));
        }
    }

    String targetName(Player player, String target) {
        if (target.equals("killer")) {
            return context.messages.text(player, "gui.boss-reward-group-list.target-killer");
        }
        return context.messages.text(player, "gui.boss-reward-group-list.target-rank")
                .replace("{rank}", target.substring("rank:".length()));
    }

    String status(Player player, boolean enabled) {
        return context.messages.text(player, "gui.boss-reward.status-" + (enabled ? "enabled" : "disabled"));
    }

    static int copiesValue(Object value) {
        if (value == null) {
            return 1;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    static int rawTotal(List<Map<?, ?>> groups) {
        long total = 0L;
        for (Map<?, ?> group : groups) {
            total += Math.max(0, copiesValue(group.get("copies")));
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    static List<Integer> rankIds(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("rewards.damage-ranking.ranks");
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream().map(value -> {
            try {
                int rank = Integer.parseInt(value);
                if (rank <= 0) {
                    throw new NumberFormatException();
                }
                return rank;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Boss rank 必须是正整数: " + value);
            }
        }).sorted().toList();
    }

    static String groupsPath(String target) {
        if (target.equals("killer")) {
            return "rewards.killer.groups";
        }
        if (target.startsWith("rank:")) {
            return "rewards.damage-ranking.ranks." + target.substring("rank:".length());
        }
        throw new IllegalArgumentException("未知 Boss 奖励目标: " + target);
    }
}
