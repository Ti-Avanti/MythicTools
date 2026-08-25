package gg.fotia.mythictools.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 单场跨阶段 Boss 战斗的运行状态。 */
final class BossFight {
    final BossConfig config;
    final String spawnerKey;
    final Map<UUID, Double> damage = new HashMap<>();
    final Map<UUID, String> playerNames = new HashMap<>();
    int phaseIndex;
    UUID entityId;

    BossFight(BossConfig config, String spawnerKey, UUID entityId) {
        this.config = config;
        this.spawnerKey = spawnerKey;
        this.entityId = entityId;
    }

    void record(UUID playerId, String playerName, double amount) {
        damage.merge(playerId, amount, Double::sum);
        playerNames.put(playerId, playerName);
    }

    boolean isFinalStage() {
        return !config.phaseMode().replacesEntityOnStageDeath()
                || phaseIndex + 1 >= config.phases().size();
    }
}
