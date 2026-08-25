package gg.fotia.mythictools.reward;

import java.util.List;

/** 单个 MythicMob 的接管掉落规则。 */
public record MobDropRule(
        String mobId,
        int maxDrops,
        int minExperience,
        int maxExperience,
        List<DropGroupRef> groups) {
    public MobDropRule {
        groups = List.copyOf(groups);
    }
}
