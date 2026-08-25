package gg.fotia.mythictools.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 先完整激活候选、再提交指针，最后清理旧实例的原子运行时切换器。 */
public final class RuntimeSwap<T extends ManagedRuntime> implements AutoCloseable {
    private final AtomicReference<T> current = new AtomicReference<>();
    private final Consumer<RuntimeException> cleanupFailureHandler;

    public RuntimeSwap(Consumer<RuntimeException> cleanupFailureHandler) {
        this.cleanupFailureHandler = Objects.requireNonNull(cleanupFailureHandler, "cleanupFailureHandler");
    }

    public synchronized void installInitial(T candidate) {
        if (current.get() != null) {
            throw new IllegalStateException("初始运行时已经安装");
        }
        activate(candidate);
        current.set(candidate);
    }

    public synchronized void replace(Supplier<? extends T> factory) {
        Objects.requireNonNull(factory, "factory");
        T candidate = Objects.requireNonNull(factory.get(), "candidate");
        activate(candidate);
        T previous = current.getAndSet(candidate);
        closeAfterCommit(previous);
    }

    public T current() {
        T value = current.get();
        if (value == null) {
            throw new IllegalStateException("运行时尚未就绪");
        }
        return value;
    }

    public T currentOrNull() {
        return current.get();
    }

    @Override
    public synchronized void close() {
        closeAfterCommit(current.getAndSet(null));
    }

    private void activate(T candidate) {
        try {
            candidate.activate();
        } catch (Exception exception) {
            try {
                candidate.close();
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("候选运行时激活失败", exception);
        }
    }

    private void closeAfterCommit(T runtime) {
        if (runtime == null) {
            return;
        }
        try {
            runtime.close();
        } catch (RuntimeException exception) {
            cleanupFailureHandler.accept(exception);
        }
    }
}
