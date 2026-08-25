package gg.fotia.mythictools.blackboxtest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;

/** 一次测试会话的文件备份，重复 begin 不会把测试内容覆盖到原始备份。 */
final class FileBackupSession {
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._-]+");
    private final Path root;

    FileBackupSession(Path root) {
        this.root = root;
    }

    void begin(Map<String, Path> files) throws IOException {
        Path active = root.resolve(".active");
        if (Files.isRegularFile(active)) {
            return;
        }
        deleteTree(root);
        Files.createDirectories(root);
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String key = requireSafeKey(entry.getKey());
            Path source = entry.getValue();
            if (Files.isRegularFile(source)) {
                Files.copy(source, root.resolve(key + ".data"), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.createFile(root.resolve(key + ".missing"));
            }
        }
        Files.createFile(active);
    }

    void restore(Map<String, Path> files) throws IOException {
        if (!Files.isRegularFile(root.resolve(".active"))) {
            return;
        }
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String key = requireSafeKey(entry.getKey());
            Path target = entry.getValue();
            Path data = root.resolve(key + ".data");
            Path missing = root.resolve(key + ".missing");
            if (Files.isRegularFile(data)) {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
            } else if (Files.isRegularFile(missing)) {
                Files.deleteIfExists(target);
            }
        }
        deleteTree(root);
    }

    private static String requireSafeKey(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("unsafe-backup-key-" + key);
        }
        return key;
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var entries = Files.walk(path)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
