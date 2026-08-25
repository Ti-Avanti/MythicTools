package gg.fotia.mythictools.config;

/** 配置加载策略：启动时隔离可选坏文件，管理员重载时拒绝任何错误。 */
public enum ConfigLoadMode {
    STARTUP_LENIENT,
    STRICT
}
