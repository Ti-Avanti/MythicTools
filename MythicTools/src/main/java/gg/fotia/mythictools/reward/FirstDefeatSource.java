package gg.fotia.mythictools.reward;

/** 首次击败记录对应的 Boss 配置或普通 MythicMob。 */
public record FirstDefeatSource(Type type, String id) {
    public FirstDefeatSource {
        if (type == null || id == null || id.isBlank()) {
            throw new IllegalArgumentException("首次击败来源不能为空");
        }
    }

    public static FirstDefeatSource boss(String id) {
        return new FirstDefeatSource(Type.BOSS, id);
    }

    public static FirstDefeatSource mob(String id) {
        return new FirstDefeatSource(Type.MOB, id);
    }

    public enum Type {
        BOSS,
        MOB
    }
}
