package com.old.silence.job.server.retry.task.support.timer;

import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.TimerTask;
import com.old.silence.job.server.task.common.timer.AbstractTimerWheel;
import com.old.silence.job.server.task.common.timer.TimerWheelConfig;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retry 任务时间轮（使用公共抽象类）
 *
 * @author mory
 */
public class RetryTimerWheel extends AbstractTimerWheel {

    private static final RetryTimerWheel INSTANCE;

    static {
        TimerWheelConfig config = TimerWheelConfig.builder()
                .tickDuration(500)  // Retry 使用 500ms tick
                .ticksPerWheel(512)
                .corePoolSize(16)   // Retry 使用 16 核心线程
                .maximumPoolSize(16)
                .threadNamePrefix("retry-task-timer-wheel-")
                .idempotentConcurrencyLevel(16)  // Retry 使用并发级别 16
                .idempotentExpireSeconds(20)
                .build();
        INSTANCE = new RetryTimerWheel(config);
        SilenceJobLog.LOCAL.info("RetryTimerWheel 初始化完成");
    }

    private RetryTimerWheel(TimerWheelConfig config) {
        super(config);
    }

    /**
     * Retry 任务添加时间轮
     *
     * @param task  任务
     * @param delay 延迟时间
     */
    public static synchronized void registerWithRetry(Supplier<TimerTask<String>> task, Duration delay) {
        INSTANCE.register(task, delay);
    }

    public static boolean checkExisted(String idempotentKey) {
        return INSTANCE.isExisted(idempotentKey);
    }

    public static void removeCache(String idempotentKey) {
        INSTANCE.clearCache(idempotentKey);
    }
}
