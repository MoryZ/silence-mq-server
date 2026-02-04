package com.old.silence.job.server.retry.task.support.idempotent;

import com.old.silence.job.server.task.common.idempotent.TimerIdempotent;

public class IdempotentHolder {

    private IdempotentHolder() {
    }

    public static TimerIdempotent getTimerIdempotent() {
        return SingletonHolder.TIMER_IDEMPOTENT;
    }

    private static class SingletonHolder {
        private static final TimerIdempotent TIMER_IDEMPOTENT = new TimerIdempotent();
    }
}
