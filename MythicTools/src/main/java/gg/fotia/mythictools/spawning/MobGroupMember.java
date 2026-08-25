package gg.fotia.mythictools.spawning;

/** 怪物组中的一个带权成员。 */
public record MobGroupMember(String id, String mobId, long weight) {
    public MobGroupMember {
        if (id == null || id.isBlank() || mobId == null || mobId.isBlank()) {
            throw new IllegalArgumentException("怪物组成员 ID 和 MythicMob ID 不能为空");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("怪物权重必须大于 0");
        }
    }
}
