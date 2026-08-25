package gg.fotia.mythictools.boss;

import java.util.Locale;

/** 最终阶段死亡时的 MythicMobs 与 MythicTools 奖励组合策略。 */
public enum FinalStageLootMode {
    LEGACY("legacy", BossDropAction.LEGACY, true),
    MYTHICTOOLS_ONLY("mythictools-only", BossDropAction.CLEAR_MYTHIC_DROPS, true),
    MYTHIC_ONLY("mythic-only", BossDropAction.KEEP_MYTHIC_DROPS, false),
    COMBINED("combined", BossDropAction.KEEP_MYTHIC_DROPS, true);

    private final String configValue;
    private final BossDropAction dropAction;
    private final boolean awardsMythicToolsRewards;

    FinalStageLootMode(String configValue, BossDropAction dropAction, boolean awardsMythicToolsRewards) {
        this.configValue = configValue;
        this.dropAction = dropAction;
        this.awardsMythicToolsRewards = awardsMythicToolsRewards;
    }

    public String configValue() {
        return configValue;
    }

    public BossDropAction dropAction() {
        return dropAction;
    }

    public boolean awardsMythicToolsRewards() {
        return awardsMythicToolsRewards;
    }

    public static FinalStageLootMode fromConfig(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (FinalStageLootMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知 Boss 最终阶段掉落策略: " + value);
    }
}
