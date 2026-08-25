package gg.fotia.mythictools.gui;

import java.util.List;

/** 奖励编辑器中由插件定义、不能自由输入的有限选项。 */
enum RewardOptionSelectorType {
    DELIVERY("delivery", List.of(
            new Option("ground", 'g'),
            new Option("inventory", 'i'))),
    EXECUTOR("executor", List.of(
            new Option("console", 'c'),
            new Option("player", 'p')));

    private final String field;
    private final List<Option> options;

    RewardOptionSelectorType(String field, List<Option> options) {
        this.field = field;
        this.options = options;
    }

    String field() {
        return field;
    }

    List<Option> options() {
        return options;
    }

    static RewardOptionSelectorType fromField(String field) {
        for (RewardOptionSelectorType selector : values()) {
            if (selector.field.equals(field)) {
                return selector;
            }
        }
        return null;
    }

    static RewardOptionSelectorType forRewardType(String rewardType) {
        return rewardType.equals("command") ? EXECUTOR : DELIVERY;
    }

    record Option(String value, char symbol) {
    }
}
