package gg.fotia.mythictools.config;

import org.bukkit.configuration.ConfigurationSection;

/** 管理员可配置的防误配置安全上限。 */
public record SafetyLimits(
        int maxDropsPerMob,
        int maxItemAmountPerGrant,
        int maxCommandExecutionsPerGrant,
        int maxExperiencePerMob,
        long maxWeight,
        int maxBossGroupCopies,
        int maxBossSelectionsPerRecipient,
        int maxBossRecipients) {

    public static final int DEFAULT_MAX_DROPS_PER_MOB = 16;
    public static final int DEFAULT_MAX_ITEM_AMOUNT_PER_GRANT = 2304;
    public static final int DEFAULT_MAX_COMMAND_EXECUTIONS_PER_GRANT = 16;
    public static final int DEFAULT_MAX_EXPERIENCE_PER_MOB = 100_000;
    public static final long DEFAULT_MAX_WEIGHT = 1_000_000L;
    public static final int DEFAULT_MAX_BOSS_GROUP_COPIES = 16;
    public static final int DEFAULT_MAX_BOSS_SELECTIONS_PER_RECIPIENT = 32;
    public static final int DEFAULT_MAX_BOSS_RECIPIENTS = 20;

    public SafetyLimits {
        requirePositive(maxDropsPerMob, "Max-Drops-Per-Mob");
        requirePositive(maxItemAmountPerGrant, "Max-Item-Amount-Per-Grant");
        requirePositive(maxCommandExecutionsPerGrant, "Max-Command-Executions-Per-Grant");
        requirePositive(maxExperiencePerMob, "Max-Experience-Per-Mob");
        requirePositive(maxWeight, "Max-Weight");
        requirePositive(maxBossGroupCopies, "Max-Boss-Group-Copies");
        requirePositive(maxBossSelectionsPerRecipient, "Max-Boss-Selections-Per-Recipient");
        requirePositive(maxBossRecipients, "Max-Boss-Recipients");
    }

    public static SafetyLimits defaults() {
        return new SafetyLimits(
                DEFAULT_MAX_DROPS_PER_MOB,
                DEFAULT_MAX_ITEM_AMOUNT_PER_GRANT,
                DEFAULT_MAX_COMMAND_EXECUTIONS_PER_GRANT,
                DEFAULT_MAX_EXPERIENCE_PER_MOB,
                DEFAULT_MAX_WEIGHT,
                DEFAULT_MAX_BOSS_GROUP_COPIES,
                DEFAULT_MAX_BOSS_SELECTIONS_PER_RECIPIENT,
                DEFAULT_MAX_BOSS_RECIPIENTS);
    }

    public static SafetyLimits load(ConfigurationSection config) {
        String root = "Safety-Limits.";
        return new SafetyLimits(
                ConfigValues.intOrDefault(config, root + "Max-Drops-Per-Mob",
                        DEFAULT_MAX_DROPS_PER_MOB, 1, Integer.MAX_VALUE),
                ConfigValues.intOrDefault(config, root + "Max-Item-Amount-Per-Grant",
                        DEFAULT_MAX_ITEM_AMOUNT_PER_GRANT, 1, Integer.MAX_VALUE),
                ConfigValues.intOrDefault(config, root + "Max-Command-Executions-Per-Grant",
                        DEFAULT_MAX_COMMAND_EXECUTIONS_PER_GRANT, 1, Integer.MAX_VALUE),
                ConfigValues.intOrDefault(config, root + "Max-Experience-Per-Mob",
                        DEFAULT_MAX_EXPERIENCE_PER_MOB, 1, Integer.MAX_VALUE),
                ConfigValues.longOrDefault(config, root + "Max-Weight",
                        DEFAULT_MAX_WEIGHT, 1L, Long.MAX_VALUE),
                ConfigValues.intOrDefault(config, root + "Max-Boss-Group-Copies",
                        DEFAULT_MAX_BOSS_GROUP_COPIES, 1, Integer.MAX_VALUE),
                ConfigValues.intOrDefault(config, root + "Max-Boss-Selections-Per-Recipient",
                        DEFAULT_MAX_BOSS_SELECTIONS_PER_RECIPIENT, 1, Integer.MAX_VALUE),
                ConfigValues.intOrDefault(config, root + "Max-Boss-Recipients",
                        DEFAULT_MAX_BOSS_RECIPIENTS, 1, Integer.MAX_VALUE));
    }

    private static void requirePositive(long value, String path) {
        if (value <= 0L) {
            throw new IllegalArgumentException("Safety-Limits." + path + " 必须大于 0");
        }
    }
}
