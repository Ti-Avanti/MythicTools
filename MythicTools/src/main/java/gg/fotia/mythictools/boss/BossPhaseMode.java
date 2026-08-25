package gg.fotia.mythictools.boss;

import java.util.Locale;

/** Boss 阶段由 MythicTools 切换实体，或交由 MythicMobs 原生技能控制。 */
public enum BossPhaseMode {
    DEATH_RESPAWN("death-respawn", true),
    MYTHIC_NATIVE("mythic-native", false);

    private final String configValue;
    private final boolean replacesEntityOnStageDeath;

    BossPhaseMode(String configValue, boolean replacesEntityOnStageDeath) {
        this.configValue = configValue;
        this.replacesEntityOnStageDeath = replacesEntityOnStageDeath;
    }

    public String configValue() {
        return configValue;
    }

    public boolean replacesEntityOnStageDeath() {
        return replacesEntityOnStageDeath;
    }

    public static BossPhaseMode fromConfig(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (BossPhaseMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知 Boss 阶段模式: " + value);
    }
}
