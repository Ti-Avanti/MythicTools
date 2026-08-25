package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.config.ConfigDomain;

/** 管理 GUI 支持的配置类型。 */
public enum AdminType {
    DROP_GROUP("drop-group", ConfigDomain.REWARDS),
    MOB_DROP("mob-drop", ConfigDomain.REWARDS),
    BIOME_RULE("biome-rule", ConfigDomain.SPAWNING),
    SPAWN_POINT("spawn-point", ConfigDomain.SPAWNING),
    MOB_GROUP("mob-group", ConfigDomain.SPAWNING),
    BOSS("boss", ConfigDomain.BOSSES);

    private final String languageKey;
    private final ConfigDomain domain;

    AdminType(String languageKey, ConfigDomain domain) {
        this.languageKey = languageKey;
        this.domain = domain;
    }

    public String languageKey() {
        return languageKey;
    }

    /** 该类型的配置文件所属的数据域，保存后只需重载此域（及其依赖域）。 */
    public ConfigDomain domain() {
        return domain;
    }
}
