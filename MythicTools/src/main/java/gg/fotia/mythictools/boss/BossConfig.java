package gg.fotia.mythictools.boss;

import java.util.List;
import java.util.Map;

/** 单个 Boss 的完整不可变配置。 */
public record BossConfig(
        String id,
        Map<String, String> displays,
        BossPhaseMode phaseMode,
        List<BossPhase> phases,
        BossPhase mythicNativePhase,
        BossLootPolicy lootPolicy,
        List<BossSpawner> spawners,
        BossBroadcast spawnBroadcast,
        BossBroadcast deathBroadcast,
        BossRewardConfig rewards) {
    public BossConfig {
        displays = Map.copyOf(displays);
        phases = List.copyOf(phases);
        spawners = List.copyOf(spawners);
        if (phaseMode == BossPhaseMode.DEATH_RESPAWN && phases.isEmpty()) {
            throw new IllegalArgumentException("death-respawn Boss 至少需要一个阶段");
        }
        if (phaseMode == BossPhaseMode.MYTHIC_NATIVE && mythicNativePhase == null) {
            throw new IllegalArgumentException("mythic-native Boss 需要配置根 MythicMob");
        }
    }

    public String display(String locale) {
        return displays.getOrDefault(locale, displays.getOrDefault("zh_CN", id));
    }

    public BossPhase initialPhase() {
        return phaseMode == BossPhaseMode.MYTHIC_NATIVE ? mythicNativePhase : phases.get(0);
    }

    public int totalPhases() {
        return phaseMode.replacesEntityOnStageDeath() ? phases.size() : 1;
    }
}
