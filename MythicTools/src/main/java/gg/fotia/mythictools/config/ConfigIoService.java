package gg.fotia.mythictools.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** 串行组织配置操作，文件工作在后台、校验与快照发布在主线程；双方都不阻塞等待。 */
public final class ConfigIoService implements AutoCloseable {
    private final File directory;
    private final Executor mainThread;
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MythicTools-ConfigIO");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    private volatile boolean closed;

    public ConfigIoService(File directory, Executor mainThread) {
        this.directory = directory;
        this.mainThread = mainThread;
    }

    public synchronized <T> CompletionStage<T> submit(
            Function<ConfigFileSnapshot, CompletionStage<T>> operation) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("配置服务已关闭"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(result);
        tail = tail.handle((ignored, failure) -> null)
                .thenCompose(ignored -> execute(io, () -> ConfigFileSnapshot.capture(directory)))
                .thenCompose(snapshot -> onMain(() -> operation.apply(snapshot)))
                .thenCompose(CompletionStage::toCompletableFuture)
                .handle((value, failure) -> {
                    pending.remove(result);
                    if (failure == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(cause(failure));
                    }
                    return null;
                });
        return result;
    }

    public CompletionStage<ConfigFileSnapshot> save(
            File file, Function<ConfigFileSnapshot, PreparedSave> prepare) {
        return submit(snapshot -> {
            PreparedSave prepared = prepare.apply(snapshot);
            ConfigFileSnapshot candidate = snapshot.with(file, prepared.contents());
            return execute(io, () -> {
                snapshot.requireUnchanged();
                YamlFiles.writeAtomically(prepared.contents().getBytes(StandardCharsets.UTF_8), file);
                return candidate;
            }).thenCompose(saved -> onMain(() -> {
                prepared.publish().run();
                return saved;
            }));
        });
    }

    public <T> CompletableFuture<T> onMain(Callable<T> action) {
        return execute(mainThread, action);
    }

    public boolean busy() {
        return !pending.isEmpty();
    }

    public static Throwable cause(Throwable failure) {
        while ((failure instanceof java.util.concurrent.CompletionException
                || failure instanceof java.util.concurrent.ExecutionException) && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    private <T> CompletableFuture<T> execute(Executor executor, Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    if (closed) {
                        throw new IllegalStateException("配置服务已关闭");
                    }
                    result.complete(action.call());
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    @Override
    public void close() {
        closed = true;
        io.shutdown();
        pending.forEach(result -> result.completeExceptionally(new IllegalStateException("配置服务已关闭")));
        pending.clear();
    }

    public record PreparedSave(String contents, Runnable publish) {
    }
}
