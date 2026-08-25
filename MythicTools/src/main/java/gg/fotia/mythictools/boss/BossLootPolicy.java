package gg.fotia.mythictools.boss;

import java.util.Objects;

/** 按 Boss 是否处于最终阶段解析战利品接管与最终奖励结算。 */
public record BossLootPolicy(
        IntermediateStageLootMode intermediateStage,
        FinalStageLootMode finalStage) {

    public BossLootPolicy {
        Objects.requireNonNull(intermediateStage, "intermediateStage");
        Objects.requireNonNull(finalStage, "finalStage");
    }

    public static BossLootPolicy legacy() {
        return new BossLootPolicy(IntermediateStageLootMode.LEGACY, FinalStageLootMode.LEGACY);
    }

    public BossDropAction dropAction(boolean finalStageDeath) {
        return finalStageDeath ? finalStage.dropAction() : intermediateStage.dropAction();
    }

    public boolean awardsMythicToolsRewardsOnFinalDeath() {
        return finalStage.awardsMythicToolsRewards();
    }
}
