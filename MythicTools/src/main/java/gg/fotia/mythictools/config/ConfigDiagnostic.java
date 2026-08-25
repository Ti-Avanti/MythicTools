package gg.fotia.mythictools.config;

import java.nio.file.Path;
import java.util.Objects;

/** 一条带来源文件与 YAML 路径的结构化配置诊断。 */
public record ConfigDiagnostic(
        ConfigSeverity severity,
        ConfigDomain domain,
        Path sourcePath,
        String yamlPath,
        ConfigProblemCode problemCode,
        String message,
        Throwable cause) {

    public ConfigDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(domain, "domain");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath").toAbsolutePath().normalize();
        yamlPath = yamlPath == null || yamlPath.isBlank() ? "$" : yamlPath;
        Objects.requireNonNull(problemCode, "problemCode");
        message = message == null ? "" : message;
    }
}
