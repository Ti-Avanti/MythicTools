package gg.fotia.mythictools.config;

/** 候选配置未通过当前加载模式的发布门禁。 */
public final class ConfigLoadException extends IllegalStateException {
    private final ConfigLoadReport report;

    public ConfigLoadException(ConfigLoadReport report) {
        super(report.summary());
        this.report = report;
    }

    public ConfigLoadReport report() {
        return report;
    }
}
