package gg.fotia.mythictools.config;

import java.util.ArrayList;
import java.util.List;

/** 一次候选配置加载的全部诊断。 */
public final class ConfigLoadReport {
    private static final ConfigLoadReport EMPTY = new ConfigLoadReport(List.of());

    private final List<ConfigDiagnostic> diagnostics;

    public ConfigLoadReport(List<ConfigDiagnostic> diagnostics) {
        this.diagnostics = List.copyOf(diagnostics);
    }

    public static ConfigLoadReport empty() {
        return EMPTY;
    }

    public static ConfigLoadReport of(ConfigDiagnostic diagnostic) {
        return new ConfigLoadReport(List.of(diagnostic));
    }

    public static ConfigLoadReport merge(ConfigLoadReport... reports) {
        List<ConfigDiagnostic> merged = new ArrayList<>();
        for (ConfigLoadReport report : reports) {
            if (report != null) {
                merged.addAll(report.diagnostics);
            }
        }
        return merged.isEmpty() ? EMPTY : new ConfigLoadReport(merged);
    }

    public List<ConfigDiagnostic> diagnostics() {
        return diagnostics;
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(value -> value.severity() != ConfigSeverity.WARNING);
    }

    public boolean blocks(ConfigLoadMode mode) {
        return diagnostics.stream().anyMatch(value -> value.severity() == ConfigSeverity.FATAL
                || mode == ConfigLoadMode.STRICT && value.severity() == ConfigSeverity.ERROR);
    }

    public String summary() {
        long warnings = diagnostics.stream().filter(value -> value.severity() == ConfigSeverity.WARNING).count();
        long errors = diagnostics.stream().filter(value -> value.severity() == ConfigSeverity.ERROR).count();
        long fatals = diagnostics.stream().filter(value -> value.severity() == ConfigSeverity.FATAL).count();
        return "配置诊断: warning=" + warnings + ", error=" + errors + ", fatal=" + fatals;
    }
}
