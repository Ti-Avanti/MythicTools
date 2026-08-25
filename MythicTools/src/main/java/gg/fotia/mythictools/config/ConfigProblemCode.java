package gg.fotia.mythictools.config;

/** 稳定的配置问题代码，便于日志、GUI 和测试识别错误类型。 */
public enum ConfigProblemCode {
    IO_ERROR,
    YAML_SYNTAX,
    MISSING_SECTION,
    MISSING_VALUE,
    INVALID_VALUE,
    MISSING_REFERENCE,
    UNKNOWN_MYTHIC_MOB,
    WORLD_UNAVAILABLE
}
