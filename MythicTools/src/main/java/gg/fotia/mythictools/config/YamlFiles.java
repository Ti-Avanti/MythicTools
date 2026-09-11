package gg.fotia.mythictools.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** YAML 加载与原子保存工具。 */
public final class YamlFiles {
    private static final ThreadLocal<ConfigFileSnapshot> SNAPSHOT = new ThreadLocal<>();
    private YamlFiles() {
    }

    /** 加载 YAML；语法错误会携带文件路径向上抛出。 */
    public static YamlConfiguration load(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigFileSnapshot snapshot = SNAPSHOT.get();
        if (snapshot == null) {
            yaml.load(file);
        } else {
            yaml.loadFromString(snapshot.read(file));
        }
        return yaml;
    }

    public static File[] list(File directory) {
        ConfigFileSnapshot snapshot = SNAPSHOT.get();
        return snapshot == null
                ? directory.listFiles((ignored, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"))
                : snapshot.list(directory);
    }

    public static <T> T using(ConfigFileSnapshot snapshot, java.util.concurrent.Callable<T> action) {
        ConfigFileSnapshot previous = SNAPSHOT.get();
        SNAPSHOT.set(snapshot);
        try {
            return action.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        } finally {
            if (previous == null) {
                SNAPSHOT.remove();
            } else {
                SNAPSHOT.set(previous);
            }
        }
    }

    /** 先写临时文件，再原子替换目标文件。 */
    public static void saveAtomically(YamlConfiguration yaml, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        File temporary = new File(parent, file.getName() + ".tmp");
        yaml.save(temporary);
        try {
            Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 原子恢复已编码完成的文件内容。 */
    public static void writeAtomically(byte[] content, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        File temporary = new File(parent, file.getName() + ".tmp");
        Files.write(temporary.toPath(), content);
        try {
            Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
