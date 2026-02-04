package com.old.silence.job.server.task.common.timer;

/**
 * 时间轮配置
 *
 * @author mory
 */
public class TimerWheelConfig {

    /**
     * Tick duration (ms)
     */
    private int tickDuration = 100;

    /**
     * 时间轮的槽数
     */
    private int ticksPerWheel = 512;

    /**
     * 线程池核心线程数
     */
    private int corePoolSize = 16;

    /**
     * 线程池最大线程数
     */
    private int maximumPoolSize = 16;

    /**
     * 线程名前缀
     */
    private String threadNamePrefix = "task-timer-wheel-";

    /**
     * 幂等性缓存并发级别
     */
    private int idempotentConcurrencyLevel = 16;

    /**
     * 幂等性缓存过期时间（秒）
     */
    private long idempotentExpireSeconds = 20;

    private TimerWheelConfig() {
    }

    public static TimerWheelConfigBuilder builder() {
        return new TimerWheelConfigBuilder();
    }

    public int getTickDuration() {
        return tickDuration;
    }

    public int getTicksPerWheel() {
        return ticksPerWheel;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public int getIdempotentConcurrencyLevel() {
        return idempotentConcurrencyLevel;
    }

    public long getIdempotentExpireSeconds() {
        return idempotentExpireSeconds;
    }

    public static class TimerWheelConfigBuilder {
        private int tickDuration = 100;
        private int ticksPerWheel = 512;
        private int corePoolSize = 16;
        private int maximumPoolSize = 16;
        private String threadNamePrefix = "task-timer-wheel-";
        private int idempotentConcurrencyLevel = 16;
        private long idempotentExpireSeconds = 20;

        private TimerWheelConfigBuilder() {
        }

        public TimerWheelConfigBuilder tickDuration(int tickDuration) {
            this.tickDuration = tickDuration;
            return this;
        }

        public TimerWheelConfigBuilder ticksPerWheel(int ticksPerWheel) {
            this.ticksPerWheel = ticksPerWheel;
            return this;
        }

        public TimerWheelConfigBuilder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public TimerWheelConfigBuilder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public TimerWheelConfigBuilder threadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
            return this;
        }

        public TimerWheelConfigBuilder idempotentConcurrencyLevel(int idempotentConcurrencyLevel) {
            this.idempotentConcurrencyLevel = idempotentConcurrencyLevel;
            return this;
        }

        public TimerWheelConfigBuilder idempotentExpireSeconds(long idempotentExpireSeconds) {
            this.idempotentExpireSeconds = idempotentExpireSeconds;
            return this;
        }

        public TimerWheelConfig build() {
            TimerWheelConfig config = new TimerWheelConfig();
            config.tickDuration = this.tickDuration;
            config.ticksPerWheel = this.ticksPerWheel;
            config.corePoolSize = this.corePoolSize;
            config.maximumPoolSize = this.maximumPoolSize;
            config.threadNamePrefix = this.threadNamePrefix;
            config.idempotentConcurrencyLevel = this.idempotentConcurrencyLevel;
            config.idempotentExpireSeconds = this.idempotentExpireSeconds;
            return config;
        }
    }
}
