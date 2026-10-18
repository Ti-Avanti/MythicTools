package gg.fotia.mythictools.summoning.config;

import org.bukkit.configuration.ConfigurationSection;

/** 拒绝非有限值、越界值和被 YAML 隐式转换的错误类型。 */
public final class SummonNumbers {
    private SummonNumbers() { }

    public static double number(ConfigurationSection yaml, String path, double fallback, double min, double max) {
        Object raw = yaml.get(path, fallback);
        if (!(raw instanceof Number value) || !Double.isFinite(value.doubleValue())
                || value.doubleValue() < min || value.doubleValue() > max) {
            throw new IllegalArgumentException(path + ": " + min + " .. " + max);
        }
        return value.doubleValue();
    }

    public static int integer(ConfigurationSection yaml, String path, int fallback, int min, int max) {
        double value = number(yaml, path, fallback, min, max);
        if (value != Math.rint(value)) throw new IllegalArgumentException(path + ": integer required");
        return (int) value;
    }

    public static String id(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("ID: [a-zA-Z0-9_-], 1..64");
        }
        return id;
    }
}
