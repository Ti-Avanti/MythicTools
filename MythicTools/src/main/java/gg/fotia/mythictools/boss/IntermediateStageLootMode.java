package gg.fotia.mythictools.boss;

import java.util.Locale;

/** 非最终阶段死亡时的战利品策略。 */
public enum IntermediateStageLootMode {
    LEGACY("legacy", BossDropAction.LEGACY),
    NONE("none", BossDropAction.CLEAR_MYTHIC_DROPS),
    MYTHIC("mythic", BossDropAction.KEEP_MYTHIC_DROPS);

    private final String configValue;
    private final BossDropAction dropAction;

    IntermediateStageLootMode(String configValue, BossDropAction dropAction) {
        this.configValue = configValue;
        this.dropAction = dropAction;
    }

    public String configValue() {
        return configValue;
    }

    public BossDropAction dropAction() {
        return dropAction;
    }

    public static IntermediateStageLootMode fromConfig(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (IntermediateStageLootMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知 Boss 中间阶段掉落策略: " + value);
    }
}
