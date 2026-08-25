package gg.fotia.mythictools.boss;

/** Boss 死亡时对 MythicMobs 原始战利品采取的动作。 */
public enum BossDropAction {
    /** 保持旧版按普通怪物掉落规则决定的行为。 */
    LEGACY,
    /** 保留 MythicMobs 配置的战利品。 */
    KEEP_MYTHIC_DROPS,
    /** 清空原版和 MythicMobs 战利品。 */
    CLEAR_MYTHIC_DROPS
}
