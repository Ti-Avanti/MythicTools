package gg.fotia.mythictools.text;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将原版旧颜色码安全转换为 MiniMessage 标签。 */
public final class LegacyColorConverter {
    private static final Pattern AMPERSAND_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern SECTION_HEX = Pattern.compile(
            "(?i)§x§([0-9a-f])§([0-9a-f])§([0-9a-f])§([0-9a-f])§([0-9a-f])§([0-9a-f])");
    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"),
            Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"),
            Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"),
            Map.entry('o', "italic"), Map.entry('r', "reset"));

    private LegacyColorConverter() {
    }

    /** 转换 &、§、&#RRGGBB 与 §x§R... 格式，保留原有 MiniMessage 标签。 */
    public static String convert(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        boolean hasSection = input.indexOf('§') >= 0;
        boolean hasAmpersand = input.indexOf('&') >= 0;
        if (!hasSection && !hasAmpersand) {
            return input;
        }
        String converted = hasSection ? replaceSectionHex(input) : input;
        if (hasAmpersand) {
            converted = AMPERSAND_HEX.matcher(converted).replaceAll("<#$1>");
        }
        StringBuilder result = new StringBuilder(converted.length() + 16);
        for (int index = 0; index < converted.length(); index++) {
            char current = converted.charAt(index);
            if ((current == '&' || current == '§') && index + 1 < converted.length()) {
                char code = Character.toLowerCase(converted.charAt(index + 1));
                String tag = TAGS.get(code);
                if (tag != null) {
                    result.append('<').append(tag).append('>');
                    index++;
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    private static String replaceSectionHex(String input) {
        Matcher matcher = SECTION_HEX.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String color = matcher.group(1) + matcher.group(2) + matcher.group(3)
                    + matcher.group(4) + matcher.group(5) + matcher.group(6);
            matcher.appendReplacement(output, "<#" + color + ">");
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
