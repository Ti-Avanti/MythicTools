package gg.fotia.mythictools.gui;

import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;

/** 识别布尔字段并计算一次点击后的值。 */
final class BooleanFieldToggle {
    private BooleanFieldToggle() {
    }

    static Optional<Boolean> next(Object current) {
        return current instanceof Boolean value ? Optional.of(!value) : Optional.empty();
    }

    static boolean apply(ConfigurationSection section, String path) {
        Optional<Boolean> toggled = next(section.get(path));
        if (toggled.isEmpty()) {
            return false;
        }
        section.set(path, toggled.get());
        return true;
    }
}
