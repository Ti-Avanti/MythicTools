package gg.fotia.mythictools.leveling;

import java.util.Locale;

/** 最近来源的距离到 MM 等级的阶梯映射。 */
public record DistanceCurve(Mode mode, double baseLevel, double safeDistance,
                            double distancePerLevel, double levelIncrease, double maxLevel) {
    public DistanceCurve {
        if (mode == null || !Double.isFinite(baseLevel) || baseLevel < 1.0
                || !Double.isFinite(safeDistance) || safeDistance < 0.0
                || !Double.isFinite(distancePerLevel) || distancePerLevel <= 0.0
                || !Double.isFinite(levelIncrease) || levelIncrease <= 0.0
                || !Double.isFinite(maxLevel) || maxLevel < baseLevel) {
            throw new IllegalArgumentException("等级参数无效：基础等级至少为 1，距离和增量必须有效，上限不能低于基础等级");
        }
    }

    public double level(double distance, double originalLevel) {
        double base = mode == Mode.ADD ? Math.max(1.0, originalLevel) : baseLevel;
        double limit = Math.max(base, maxLevel);
        double steps = Math.floor(Math.max(0.0, distance - safeDistance) / distancePerLevel);
        // 先比较上限，避免极端配置中的乘法溢出；add 模式不会降低原有等级。
        if (steps >= (limit - base) / levelIncrease) {
            return limit;
        }
        return Math.min(limit, base + steps * levelIncrease);
    }

    public enum Mode {
        REPLACE, ADD;

        public static Mode parse(String text) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("level-mode 只能是 replace 或 add", exception);
            }
        }
    }
}
