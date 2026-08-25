package gg.fotia.mythictools.gui;

import java.util.List;

/** 从当前已加载配置中选择能通过重载验证的新建草稿默认值。 */
final class CreationDraftDefaults {
    private CreationDraftDefaults() {
    }

    static SpawnSource spawnSource(List<String> mobIds, List<String> mobGroupIds) {
        String mobId = firstId(mobIds);
        if (mobId != null) {
            return new SpawnSource(mobId, null);
        }
        String mobGroupId = firstId(mobGroupIds);
        if (mobGroupId != null) {
            return new SpawnSource(null, mobGroupId);
        }
        throw new IllegalStateException("没有可用的 MythicMob 或怪物组，无法创建刷怪配置");
    }

    static String requireMob(List<String> mobIds, String targetLabel) {
        String mobId = firstId(mobIds);
        if (mobId == null) {
            throw new IllegalStateException("没有可用的 MythicMob，无法创建" + targetLabel);
        }
        return mobId;
    }

    static String firstId(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse(null);
    }

    record SpawnSource(String mobId, String mobGroupId) {
    }
}
