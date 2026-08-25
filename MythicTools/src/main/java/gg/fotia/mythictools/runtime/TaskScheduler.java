package gg.fotia.mythictools.runtime;

/** 与 Bukkit 解耦的主线程任务调度端口。 */
public interface TaskScheduler {
    TaskHandle execute(Runnable command);

    TaskHandle later(Runnable command, long delayTicks);

    TaskHandle repeating(Runnable command, long delayTicks, long periodTicks);
}
