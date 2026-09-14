package gg.fotia.mythictools.leveling;

import gg.fotia.mythictools.config.ConfigDiagnostic;
import gg.fotia.mythictools.config.ConfigDomain;
import gg.fotia.mythictools.config.ConfigProblemCode;
import gg.fotia.mythictools.config.ConfigSeverity;
import gg.fotia.mythictools.config.YamlFiles;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/** 距离等级配置的路径与诊断共用规则。 */
final class LevelingConfigFiles {
    private LevelingConfigFiles() { }

    static List<File> list(File root, String directory) {
        File[] files = YamlFiles.list(new File(root, "leveling/" + directory));
        return files == null ? List.of() : Arrays.stream(files)
                .sorted(java.util.Comparator.comparing(File::getName)).toList();
    }

    static String id(File file) {
        return file.getName().substring(0, file.getName().length() - 4);
    }

    static String required(ConfigurationSection yaml, String path) {
        String value = yaml.getString(path, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path + " 不能为空");
        }
        return value;
    }

    static boolean enabled(ConfigurationSection yaml, boolean fallback) {
        Object value = yaml.get("enabled");
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        throw new IllegalArgumentException("enabled 必须是 true 或 false");
    }

    static ConfigDiagnostic problem(File file, ConfigSeverity severity, ConfigProblemCode code,
                                    String message, Throwable cause) {
        return new ConfigDiagnostic(severity, ConfigDomain.LEVELING, file.toPath(), "$", code, message, cause);
    }
}
