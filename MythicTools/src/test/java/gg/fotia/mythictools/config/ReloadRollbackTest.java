package gg.fotia.mythictools.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReloadRollbackTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresDeletedFileWhenReloadFails() throws Exception {
        Path target = tempDir.resolve("group.yml");
        byte[] original = "amount:\n  min: 1\n".getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);

        assertThrows(IllegalStateException.class, () -> ReloadRollback.mutate(
                target,
                () -> Files.delete(target),
                () -> {
                    throw new IllegalStateException("reload failed");
                }));

        assertArrayEquals(original, Files.readAllBytes(target));
    }
}
