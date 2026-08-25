package gg.fotia.mythictools.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 在配置变更后的运行重载失败时，恢复变更前的文件。 */
public final class ReloadRollback {
    private ReloadRollback() {
    }

    @FunctionalInterface
    public interface IoMutation {
        void run() throws IOException;
    }

    public static void mutate(Path target, IoMutation mutation, Runnable reload) throws IOException {
        boolean existed = Files.exists(target);
        byte[] original = existed ? Files.readAllBytes(target) : null;
        mutation.run();
        try {
            reload.run();
        } catch (RuntimeException exception) {
            try {
                if (existed) {
                    YamlFiles.writeAtomically(original, target.toFile());
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException restoreException) {
                exception.addSuppressed(restoreException);
            }
            // 原子严格重载在抛出异常前从未发布候选快照；恢复磁盘后保留原指针即可。
            // 其他旧式重载仍执行一次恢复重载，保持原有恢复语义。
            if (!(exception instanceof ConfigLoadException)) {
                try {
                    reload.run();
                } catch (RuntimeException restoreReloadException) {
                    exception.addSuppressed(restoreReloadException);
                }
            }
            throw exception;
        }
    }
}
