package gg.fotia.mythictools.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 后台读取的原始配置文件快照；Bukkit YAML 反序列化仍由主线程完成。 */
public final class ConfigFileSnapshot {
    private final Path root;
    private final Map<Path, byte[]> files;

    private ConfigFileSnapshot(Path root, Map<Path, byte[]> files) {
        this.root = root;
        this.files = Map.copyOf(files);
    }

    public static ConfigFileSnapshot capture(File directory) throws IOException {
        Path root = path(directory);
        Map<Path, byte[]> files = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(value -> value.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted().toList()) {
                files.put(file, Files.readAllBytes(file));
            }
        }
        return new ConfigFileSnapshot(root, files);
    }

    public String read(File file) throws IOException {
        byte[] bytes = files.get(path(file));
        if (bytes == null) {
            throw new NoSuchFileException(file.getPath());
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public ConfigFileSnapshot with(File file, String contents) {
        Path target = path(file);
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("配置文件不在插件目录中");
        }
        Map<Path, byte[]> changed = new LinkedHashMap<>(files);
        changed.put(target, contents.getBytes(StandardCharsets.UTF_8));
        return new ConfigFileSnapshot(root, changed);
    }

    File[] list(File directory) {
        Path parent = path(directory);
        return files.keySet().stream().filter(file -> file.getParent().equals(parent))
                .sorted().map(Path::toFile).toArray(File[]::new);
    }

    /** 写盘前在 I/O 线程重新核对整个校验输入，避免覆盖外部或其他管理员的改动。 */
    void requireUnchanged() throws IOException {
        ConfigFileSnapshot current = capture(root.toFile());
        if (!files.keySet().equals(current.files.keySet()) || files.entrySet().stream()
                .anyMatch(entry -> !Arrays.equals(entry.getValue(), current.files.get(entry.getKey())))) {
            throw new java.util.ConcurrentModificationException("Configuration changed during save");
        }
    }

    private static Path path(File file) {
        return file.toPath().toAbsolutePath().normalize();
    }
}
