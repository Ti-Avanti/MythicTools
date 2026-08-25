package gg.fotia.mythictools.gui;

/** 配置字段路径片段的显示规则。 */
final class FieldPathSegments {
    private FieldPathSegments() {
    }

    static int displayIndex(String segment) {
        return Integer.parseInt(segment);
    }
}
