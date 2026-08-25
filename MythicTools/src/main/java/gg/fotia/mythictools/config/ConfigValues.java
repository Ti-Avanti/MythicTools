package gg.fotia.mythictools.config;

import java.math.BigInteger;
import org.bukkit.configuration.ConfigurationSection;

/** 从 YAML 原始值执行无截断的数值读取与边界校验。 */
public final class ConfigValues {
    private ConfigValues() {
    }

    public static int requireInt(ConfigurationSection section, String path, int minimum, int maximum) {
        return requireIntValue(section.get(path), path, minimum, maximum);
    }

    public static int intOrDefault(
            ConfigurationSection section,
            String path,
            int fallback,
            int minimum,
            int maximum) {
        Object raw = section.get(path);
        return raw == null ? requireRange(fallback, minimum, maximum, path)
                : requireIntValue(raw, path, minimum, maximum);
    }

    public static long requireLong(ConfigurationSection section, String path, long minimum, long maximum) {
        return requireLongValue(section.get(path), path, minimum, maximum);
    }

    public static long longOrDefault(
            ConfigurationSection section,
            String path,
            long fallback,
            long minimum,
            long maximum) {
        Object raw = section.get(path);
        return raw == null ? requireRange(fallback, minimum, maximum, path)
                : requireLongValue(raw, path, minimum, maximum);
    }

    public static int intValueOrDefault(
            Object raw,
            int fallback,
            String path,
            int minimum,
            int maximum) {
        return raw == null ? requireRange(fallback, minimum, maximum, path)
                : requireIntValue(raw, path, minimum, maximum);
    }

    public static long longValueOrDefault(
            Object raw,
            long fallback,
            String path,
            long minimum,
            long maximum) {
        return raw == null ? requireRange(fallback, minimum, maximum, path)
                : requireLongValue(raw, path, minimum, maximum);
    }

    public static double doubleOrDefault(
            ConfigurationSection section,
            String path,
            double fallback,
            double minimum,
            double maximum) {
        return doubleValueOrDefault(section.get(path), fallback, path, minimum, maximum);
    }

    public static double doubleValueOrDefault(
            Object raw,
            double fallback,
            String path,
            double minimum,
            double maximum) {
        double value;
        if (raw == null) {
            value = fallback;
        } else if (raw instanceof Number number) {
            value = number.doubleValue();
        } else {
            throw invalid(path, "必须是数值");
        }
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw invalid(path, "必须在 " + minimum + " 到 " + maximum + " 之间且为有限数值");
        }
        return value;
    }

    public static void requireOrdered(long minimum, long maximum, String minimumPath, String maximumPath) {
        if (maximum < minimum) {
            throw invalid(maximumPath, "必须大于等于 " + minimumPath);
        }
    }

    public static void requireOrdered(double minimum, double maximum, String minimumPath, String maximumPath) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum) {
            throw invalid(maximumPath, "必须大于等于 " + minimumPath + " 且为有限数值");
        }
    }

    public static long addExact(long left, long right, String path) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(path + " 总和超出 64 位整数范围", exception);
        }
    }

    private static int requireIntValue(Object raw, String path, int minimum, int maximum) {
        long value = requireIntegral(raw, path);
        if (value < minimum || value > maximum) {
            throw invalid(path, "必须在 " + minimum + " 到 " + maximum + " 之间");
        }
        return (int) value;
    }

    private static long requireLongValue(Object raw, String path, long minimum, long maximum) {
        long value = requireIntegral(raw, path);
        return requireRange(value, minimum, maximum, path);
    }

    private static long requireIntegral(Object raw, String path) {
        if (raw == null) {
            throw invalid(path, "缺少整数值");
        }
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof BigInteger value) {
            try {
                return value.longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(path + " 超出 64 位整数范围", exception);
            }
        }
        throw invalid(path, "必须是整数，不能使用小数或文本");
    }

    private static int requireRange(int value, int minimum, int maximum, String path) {
        if (value < minimum || value > maximum) {
            throw invalid(path, "必须在 " + minimum + " 到 " + maximum + " 之间");
        }
        return value;
    }

    private static long requireRange(long value, long minimum, long maximum, String path) {
        if (value < minimum || value > maximum) {
            throw invalid(path, "必须在 " + minimum + " 到 " + maximum + " 之间");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String path, String reason) {
        return new IllegalArgumentException(path + " " + reason);
    }
}
