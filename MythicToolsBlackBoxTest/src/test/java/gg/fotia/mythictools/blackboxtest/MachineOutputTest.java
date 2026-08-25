package gg.fotia.mythictools.blackboxtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MachineOutputTest {
    @Test
    void formatsOneLineWithStableOrderAndEscapedWhitespace() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("success", false);
        values.put("reason", "bad config\nline 2");

        assertEquals(
                "MTTEST_RELOAD success=false reason=bad_config_line_2",
                MachineOutput.format("reload", values));
    }
}
