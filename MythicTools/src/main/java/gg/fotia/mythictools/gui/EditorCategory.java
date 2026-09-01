package gg.fotia.mythictools.gui;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** 将复杂配置字段分配到玩家容易理解的编辑分类。 */
enum EditorCategory {
    BIOME_BASIC(AdminType.BIOME_RULE, "biome-basic",
            exact("enabled", "mob", "mob-group", "level", "despawn-seconds"), null),
    BIOME_TRIGGER(AdminType.BIOME_RULE, "biome-trigger",
            exact("worlds", "biomes", "chance", "interval-seconds"), null),
    BIOME_LOCATION(AdminType.BIOME_RULE, "biome-location",
            prefixed("min-distance", "max-distance", "height.", "light."), null),
    BIOME_LIMITS(AdminType.BIOME_RULE, "biome-limits",
            prefixed("amount.", "limits."), null),

    MOB_DROP_BASIC(AdminType.MOB_DROP, "mob-drop-basic",
            exact("mob-id", "max-drops", "experience.min", "experience.max"), null),
    MOB_DROP_GROUPS(AdminType.MOB_DROP, "mob-drop-groups",
            prefixed("groups"), null),
    MOB_DROP_FIRST_DEFEAT(AdminType.MOB_DROP, "mob-drop-first-defeat",
            prefixed("first-defeat."), null),

    DROP_ITEM_REWARDS(AdminType.DROP_GROUP, "drop-item-rewards", ignored -> false, "item"),
    DROP_COMMAND_REWARDS(AdminType.DROP_GROUP, "drop-command-rewards", ignored -> false, "command"),

    MOB_GROUP_AMOUNT(AdminType.MOB_GROUP, "mob-group-amount", exact("amount.min", "amount.max"), null),
    MOB_GROUP_MEMBERS(AdminType.MOB_GROUP, "mob-group-members", ignored -> false, null),

    BOSS_BASIC(AdminType.BOSS, "boss-basic",
            path -> path.equals("display") || path.startsWith("display.")
                    || path.equals("phase-mode") || path.startsWith("phases")
                    || path.startsWith("mythic-native."), null),
    BOSS_LOOT(AdminType.BOSS, "boss-loot", prefixed("loot."), null),
    BOSS_BIOME_SPAWNING(AdminType.BOSS, "boss-biome-spawning",
            prefixed("spawning.biome."), null),
    BOSS_POINT_SPAWNING(AdminType.BOSS, "boss-point-spawning",
            path -> path.startsWith("spawning.points.")
                    && !path.contains(".schedule."), null),
    BOSS_POINT_SCHEDULE(AdminType.BOSS, "boss-point-schedule", ignored -> false, null),
    BOSS_BROADCASTS(AdminType.BOSS, "boss-broadcasts", prefixed("broadcast."), null),
    BOSS_RANKING_REWARDS(AdminType.BOSS, "boss-ranking-rewards",
            prefixed("rewards.damage-ranking."), null),
    BOSS_KILLER_REWARDS(AdminType.BOSS, "boss-killer-rewards",
            prefixed("rewards.killer."), null),
    BOSS_FIRST_DEFEAT(AdminType.BOSS, "boss-first-defeat",
            prefixed("rewards.first-defeat."), null);

    private final AdminType type;
    private final String key;
    private final Predicate<String> fieldMatcher;
    private final String rewardType;

    EditorCategory(AdminType type, String key, Predicate<String> fieldMatcher, String rewardType) {
        this.type = type;
        this.key = key;
        this.fieldMatcher = fieldMatcher;
        this.rewardType = rewardType;
    }

    String key() {
        return key;
    }

    boolean acceptsField(String path) {
        return fieldMatcher.test(path);
    }

    boolean acceptsRewardType(String value) {
        return rewardType != null && rewardType.equalsIgnoreCase(value);
    }

    static List<EditorCategory> forType(AdminType type) {
        return Arrays.stream(values()).filter(category -> category.type == type).toList();
    }

    static EditorCategory byKey(AdminType type, String key) {
        return forType(type).stream().filter(category -> category.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知编辑分类: " + key));
    }

    private static Predicate<String> exact(String... values) {
        List<String> accepted = List.of(values);
        return accepted::contains;
    }

    private static Predicate<String> prefixed(String... prefixes) {
        return path -> Arrays.stream(prefixes).anyMatch(path::startsWith);
    }
}
