package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.ConfigValues;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** 怪物组成员专用编辑草稿。 */
final class MobMemberDraft {
    private String mobId;
    private long weight;

    private MobMemberDraft(String mobId, long weight) {
        this.mobId = mobId;
        this.weight = weight;
    }

    static MobMemberDraft create(String memberId) {
        return new MobMemberDraft(memberId, 100L);
    }

    static MobMemberDraft load(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("怪物组成员不存在: " + path);
        }
        return new MobMemberDraft(
                section.getString("mob", ""),
                ConfigValues.longOrDefault(section, "weight", 1L, 1L, Long.MAX_VALUE));
    }

    String mobId() {
        return mobId;
    }

    void mobId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MythicMob ID 不能为空");
        }
        mobId = value.trim();
    }

    long weight() {
        return weight;
    }

    void weight(long value) {
        if (value <= 0L) {
            throw new IllegalArgumentException("权重必须大于 0");
        }
        weight = value;
    }

    void applyTo(YamlConfiguration yaml, String path) {
        yaml.set(path, null);
        yaml.set(path + ".mob", mobId);
        yaml.set(path + ".weight", weight);
    }
}
