package gg.fotia.mythictools.boss;

import java.util.UUID;

/** 战斗标识跨阶段保持稳定，实体标识只代表初始阶段。 */
public record BossSpawnResult(UUID fightId, UUID entityId) { }
