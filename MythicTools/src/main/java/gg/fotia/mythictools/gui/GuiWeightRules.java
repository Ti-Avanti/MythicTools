package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.SafetyLimits;

/** GUI 保存前使用的统一权重边界。 */
final class GuiWeightRules {
    private GuiWeightRules() {
    }

    static long requireValid(long value, SafetyLimits limits) {
        if (value < 1L || value > limits.maxWeight()) {
            throw new IllegalArgumentException("权重必须在 1 到 " + limits.maxWeight() + " 之间");
        }
        return value;
    }
}
