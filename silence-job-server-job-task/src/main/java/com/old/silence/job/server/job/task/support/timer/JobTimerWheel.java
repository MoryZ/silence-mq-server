package com.old.silence.job.server.job.task.support.timer;

import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.TimerTask;
import com.old.silence.job.server.task.common.timer.AbstractTimerWheel;
import com.old.silence.job.server.task.common.timer.TimerWheelConfig;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Job 任务时间轮（使用公共抽象类）
 *
 * @author mory
 */
public class JobTimerWheel extends AbstractTimerWheel {

    private static final JobTimerWheel INSTANCE;

    static {
        TimerWheelConfig config = TimerWheelConfig.builder()
                .tickDuration(100)  // Job 使用 100ms tick
                .ticksPerWheel(512)
                .corePoolSize(32)   // Job 使用 32 核心线程
                .maximumPoolSize(32)
                .threadNamePrefix("job-task-timer-wheel-")
                .idempotentConcurrencyLevel(8)  // Job 使用并发级别 8
                .idempotentExpireSeconds(20)
                .build();
        INSTANCE = new JobTimerWheel(config);
        SilenceJobLog.LOCAL.info("JobTimerWheel 初始化完成");
    }

    private JobTimerWheel(TimerWheelConfig config) {
        super(config);
    }

    /**
     * 定时任务添加时间轮
     *
     * @param task  任务
     * @param delay 延迟时间
     */
    public static synchronized void registerWithWorkflow(Supplier<TimerTask<String>> task, Duration delay) {
        registerWithJob(task, delay);
    }

    /**
     * 工作流任务添加时间轮
     * 虽然job和Workflow 添加时间轮方法逻辑一样为了后面做一些不同的逻辑，这里兼容分开写
     *
     * @param task  任务
     * @param delay 延迟时间
     */
    public static synchronized void registerWithJob(Supplier<TimerTask<String>> task, Duration delay) {
        INSTANCE.register(task, delay);
    }

    public static boolean checkExisted(String idempotentKey) {
        return INSTANCE.isExisted(idempotentKey);
    }

    public static void removeCache(String idempotentKey) {
        INSTANCE.clearCache(idempotentKey);
    }
}
