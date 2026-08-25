package gg.fotia.mythictools.blackboxtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBackupSessionTest {
    @TempDir
    Path tempDirectory;

    @Test
    void restoresOriginalFilesAndRemovesFilesThatWereOriginallyAbsent() throws Exception {
        Path original = tempDirectory.resolve("target/original.yml");
        Path absent = tempDirectory.resolve("target/absent.yml");
        Files.createDirectories(original.getParent());
        Files.writeString(original, "original");
        Map<String, Path> files = new LinkedHashMap<>();
        files.put("original", original);
        files.put("absent", absent);
        FileBackupSession session = new FileBackupSession(tempDirectory.resolve("backup"));

        session.begin(files);
        Files.writeString(original, "test");
        Files.writeString(absent, "created");
        session.begin(files);
        session.restore(files);
        session.restore(files);

        assertEquals("original", Files.readString(original));
        assertFalse(Files.exists(absent));
        assertFalse(Files.exists(tempDirectory.resolve("backup")));
    }
}
