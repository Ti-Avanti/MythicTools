package gg.fotia.mythictools.boss;

import gg.fotia.mythictools.reward.FirstDefeatRecipient;
import gg.fotia.mythictools.reward.FirstDefeatScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 纯计算 Boss 首次奖励的接收者顺序。 */
final class BossFirstDefeatRecipients {
    private BossFirstDefeatRecipients() {
    }

    static List<UUID> select(
            FirstDefeatScope scope,
            FirstDefeatRecipient recipient,
            Map<UUID, Double> damage,
            UUID killer) {
        if (killer == null) {
            return List.of();
        }
        if (scope == FirstDefeatScope.SERVER || recipient == FirstDefeatRecipient.KILLER) {
            return List.of(killer);
        }
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(damage.entrySet());
        ranking.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder())
                .thenComparing(entry -> entry.getKey().toString()));
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        ranking.forEach(entry -> result.add(entry.getKey()));
        result.add(killer);
        return List.copyOf(result);
    }
}
