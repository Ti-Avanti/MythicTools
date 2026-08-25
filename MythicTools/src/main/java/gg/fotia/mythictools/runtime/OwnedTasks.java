package gg.fotia.mythictools.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 记录确切任务句柄，并用代际令牌阻断已取消回调。 */
public final class OwnedTasks implements AutoCloseable {
    private final TaskScheduler scheduler;
    private final List<OwnedHandle> handles = new ArrayList<>();
    private long generation;
    private boolean accepting = true;

    public OwnedTasks(TaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public synchronized void beginGeneration() {
        cancelOwnedHandles();
        generation++;
        accepting = true;
    }

    public TaskHandle execute(Runnable command) {
        return schedule(command, 0L, 0L, ScheduleType.NOW);
    }

    public TaskHandle later(Runnable command, long delayTicks) {
        return schedule(command, delayTicks, 0L, ScheduleType.LATER);
    }

    public TaskHandle repeating(Runnable command, long delayTicks, long periodTicks) {
        return schedule(command, delayTicks, periodTicks, ScheduleType.REPEATING);
    }

    public synchronized void cancelAll() {
        accepting = false;
        generation++;
        cancelOwnedHandles();
    }

    @Override
    public void close() {
        cancelAll();
    }

    private TaskHandle schedule(Runnable command, long delayTicks, long periodTicks, ScheduleType type) {
        Objects.requireNonNull(command, "command");
        final long scheduledGeneration;
        synchronized (this) {
            if (!accepting) {
                throw new IllegalStateException("任务所有者已关闭");
            }
            scheduledGeneration = generation;
        }
        OwnedHandle owned = new OwnedHandle();
        boolean oneShot = type != ScheduleType.REPEATING;
        Runnable guarded = () -> {
            synchronized (this) {
                if (!accepting || generation != scheduledGeneration || owned.cancelled || owned.completed) {
                    if (oneShot) {
                        owned.complete();
                    }
                    return;
                }
            }
            try {
                command.run();
            } finally {
                if (oneShot) {
                    owned.complete();
                }
            }
        };
        TaskHandle delegate = switch (type) {
            case NOW -> scheduler.execute(guarded);
            case LATER -> scheduler.later(guarded, delayTicks);
            case REPEATING -> scheduler.repeating(guarded, delayTicks, periodTicks);
        };
        owned.attach(delegate);
        synchronized (this) {
            if (!accepting || generation != scheduledGeneration) {
                owned.cancel();
            } else if (!owned.completed) {
                handles.add(owned);
            }
        }
        return owned;
    }

    private void cancelOwnedHandles() {
        List<OwnedHandle> copy = List.copyOf(handles);
        handles.clear();
        for (OwnedHandle handle : copy) {
            try {
                handle.cancel();
            } catch (RuntimeException ignored) {
                // 继续取消同一所有者的其余句柄。
            }
        }
    }

    private final class OwnedHandle implements TaskHandle {
        private TaskHandle delegate;
        private boolean completed;
        private boolean cancelled;

        private void attach(TaskHandle delegate) {
            boolean cancelNow;
            synchronized (OwnedTasks.this) {
                this.delegate = Objects.requireNonNull(delegate, "delegate");
                cancelNow = cancelled;
            }
            if (cancelNow) {
                delegate.cancel();
            }
        }

        private void complete() {
            synchronized (OwnedTasks.this) {
                completed = true;
                handles.remove(this);
            }
        }

        @Override
        public void cancel() {
            TaskHandle current;
            synchronized (OwnedTasks.this) {
                if (cancelled || completed) {
                    return;
                }
                cancelled = true;
                handles.remove(this);
                current = delegate;
            }
            if (current != null) {
                current.cancel();
            }
        }
    }

    private enum ScheduleType {
        NOW,
        LATER,
        REPEATING
    }
}
