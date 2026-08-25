package gg.fotia.mythictools.gui;

import java.util.List;

/** Boss 排名与击杀奖励共享的份数安全规则。 */
final class BossRewardSelectionRules {
    private BossRewardSelectionRules() {
    }

    static void requireCopies(int copies, int maximum) {
        if (copies < 1 || copies > maximum) {
            throw new IllegalArgumentException("copies must be between 1 and " + maximum);
        }
    }

    static void requireTotal(List<Integer> copies, int maximum) {
        long total = 0L;
        for (int value : copies) {
            requireCopies(value, Integer.MAX_VALUE);
            total = Math.addExact(total, value);
        }
        if (total > maximum) {
            throw new IllegalArgumentException("recipient selections exceed " + maximum);
        }
    }

    static void requireCombined(
            boolean rankingEnabled,
            List<List<Integer>> rankingCopies,
            boolean killerEnabled,
            List<Integer> killerCopies,
            int maximum) {
        if (!rankingEnabled || !killerEnabled) {
            return;
        }
        long killerTotal = sum(killerCopies);
        for (List<Integer> rankCopies : rankingCopies) {
            if (Math.addExact(sum(rankCopies), killerTotal) > maximum) {
                throw new IllegalArgumentException("combined recipient selections exceed " + maximum);
            }
        }
    }

    private static long sum(List<Integer> copies) {
        long total = 0L;
        for (int value : copies) {
            if (value < 1) {
                throw new IllegalArgumentException("copies must be positive");
            }
            total = Math.addExact(total, value);
        }
        return total;
    }
}
