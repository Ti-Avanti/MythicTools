package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FieldPathSegmentsTest {
    @Test
    void preservesOneBasedNumericConfigurationKeys() {
        assertEquals(1, FieldPathSegments.displayIndex("1"));
        assertEquals(2, FieldPathSegments.displayIndex("2"));
        assertEquals(3, FieldPathSegments.displayIndex("3"));
    }
}
