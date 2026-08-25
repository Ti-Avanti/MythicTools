package gg.fotia.mythictools.runtime;

/** 可精确取消的单个调度任务。 */
@FunctionalInterface
public interface TaskHandle {
    void cancel();
}
