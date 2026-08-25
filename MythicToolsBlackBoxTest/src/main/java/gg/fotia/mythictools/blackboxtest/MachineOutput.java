package gg.fotia.mythictools.blackboxtest;

import java.util.Map;
import java.util.stream.Collectors;

/** 生成 BlackBoxPro 聊天查询可稳定解析的单行 key=value 输出。 */
final class MachineOutput {
    private MachineOutput() {
    }

    static String format(String type, Map<String, ?> values) {
        String suffix = values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + sanitize(entry.getValue()))
                .collect(Collectors.joining(" "));
        return "MTTEST_" + type.toUpperCase(java.util.Locale.ROOT)
                + (suffix.isEmpty() ? "" : " " + suffix);
    }

    private static String sanitize(Object value) {
        return String.valueOf(value).replaceAll("[\\s=]+", "_");
    }
}
