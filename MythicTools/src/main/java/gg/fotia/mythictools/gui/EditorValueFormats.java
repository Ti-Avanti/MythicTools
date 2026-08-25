package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.text.MessageRenderer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

/** 编辑器字段值的解析、格式化与标签本地化。 */
final class EditorValueFormats {
    private final MessageRenderer messages;

    EditorValueFormats(MessageRenderer messages) {
        this.messages = messages;
    }

    Object parseValue(Player player, Object current, String input) {
        if (input.equalsIgnoreCase("null")) {
            return null;
        }
        if (current instanceof Boolean) {
            if (!input.equalsIgnoreCase("true") && !input.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException(messages.text(player, "gui.editor.validation.boolean"));
            }
            return Boolean.parseBoolean(input);
        }
        if (current instanceof Integer) {
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(messages.text(player, "gui.editor.validation.integer"), exception);
            }
        }
        if (current instanceof Long) {
            try {
                return Long.parseLong(input);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(messages.text(player, "gui.editor.validation.integer"), exception);
            }
        }
        if (current instanceof Number) {
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(messages.text(player, "gui.editor.validation.number"), exception);
            }
        }
        if (current instanceof List<?> list) {
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                return parseMapList(player, input);
            }
            String delimiter = input.contains(";") ? ";" : ",";
            return java.util.Arrays.stream(input.split(Pattern.quote(delimiter)))
                    .map(String::trim).filter(value -> !value.isEmpty()).toList();
        }
        return input;
    }

    private List<Map<String, Object>> parseMapList(Player player, String input) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String objectText : input.split(";")) {
            Map<String, Object> object = new LinkedHashMap<>();
            for (String pair : objectText.split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    throw new IllegalArgumentException(messages.text(player, "gui.editor.validation.map-list"));
                }
                String key = pair.substring(0, equals).trim();
                String value = pair.substring(equals + 1).trim();
                object.put(key, inferScalar(value));
            }
            result.add(object);
        }
        return result;
    }

    private static Object inferScalar(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
            List<String> objects = new ArrayList<>();
            for (Object raw : list) {
                Map<?, ?> map = (Map<?, ?>) raw;
                objects.add(map.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(java.util.stream.Collectors.joining(",")));
            }
            return String.join(";", objects);
        }
        if (value instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        String text = String.valueOf(value);
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

    String inputHint(Player player, Object current) {
        String key;
        if (current instanceof Boolean) {
            key = "boolean";
        } else if (current instanceof Integer || current instanceof Long) {
            key = "integer";
        } else if (current instanceof Number) {
            key = "number";
        } else if (current instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
            key = "map-list";
        } else if (current instanceof List<?>) {
            key = "list";
        } else {
            key = "text";
        }
        return messages.text(player, "gui.editor.input-hints." + key);
    }

    String fieldLabel(Player player, String field) {
        String exactKey = "gui.editor.field-labels." + field.replace(".", "__");
        if (messages.containsText(player, exactKey)) {
            return messages.text(player, exactKey);
        }
        List<String> labels = new ArrayList<>();
        for (String segment : field.split("\\.")) {
            if (segment.chars().allMatch(Character::isDigit)) {
                int index = FieldPathSegments.displayIndex(segment);
                labels.add(messages.text(player, "gui.editor.index-label")
                        .replace("{index}", String.valueOf(index)));
                continue;
            }
            String segmentKey = "gui.editor.field-segments." + segment;
            labels.add(messages.containsText(player, segmentKey)
                    ? messages.text(player, segmentKey)
                    : segment);
        }
        return String.join(messages.text(player, "gui.editor.path-separator"), labels);
    }

    static Object editorValue(EditorSession session, String field) {
        Object value = session.yaml.get(absolutePath(session, field));
        if (value != null) {
            return value;
        }
        return switch (field) {
            case "phase-mode" -> "death-respawn";
            case "mythic-native.level" -> 1.0;
            case "loot.intermediate-stage", "loot.final-stage" -> "legacy";
            default -> BossSpawnerEditorFields.defaultValue(field);
        };
    }

    static String absolutePath(EditorSession session, String relative) {
        return session.rootPath.isEmpty() ? relative : session.rootPath + "." + relative;
    }
}
