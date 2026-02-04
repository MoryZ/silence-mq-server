package com.old.silence.job.server.retry.task.support.prepare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.old.silence.job.common.enums.RetryTaskStatus;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.retry.task.dto.RetryTaskPrepareDTO;
import com.old.silence.job.server.retry.task.support.RetryPrePareHandler;
import com.old.silence.job.server.retry.task.support.RetryTaskConverter;
import com.old.silence.job.server.retry.task.support.timer.RetryTimerContext;
import com.old.silence.job.server.retry.task.support.timer.RetryTimerTask;
import com.old.silence.job.server.retry.task.support.timer.RetryTimerWheel;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;


@Component

public class WaitRetryPrepareHandler implements RetryPrePareHandler  {


    private static final Logger log = LoggerFactory.getLogger(WaitRetryPrepareHandler.class);

    @Override
    public boolean matches(RetryTaskStatus status) {
        return Objects.equals(RetryTaskStatus.WAITING, status);
    }

    @Override
    public void handle(RetryTaskPrepareDTO prepare) {
        // 若时间轮中数据不存在则重新加入
        if (!RetryTimerWheel.checkExisted(MessageFormat.format(RetryTimerTask.IDEMPOTENT_KEY_PREFIX, prepare.getRetryTaskId()))) {
            log.info("存在待处理任务且时间轮中不存在 retryTaskId:[{}]", prepare.getRetryTaskId());

            // 进入时间轮
            long delay = prepare.getNextTriggerAt() - DateUtils.toNowMilli();
            RetryTimerContext timerContext = RetryTaskConverter.INSTANCE.toRetryTimerContext(prepare);
            RetryTimerWheel.registerWithRetry(() -> new RetryTimerTask(timerContext), Duration.ofMillis(delay));
        }
    }
}
