package com.old.silence.job.server.retry.task.support.generator.task;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import com.old.silence.job.common.enums.RetryTaskStatus;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.domain.model.RetryTask;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.RetryTaskDao;
import com.old.silence.job.server.retry.task.dto.RetryTaskGeneratorDTO;
import com.old.silence.job.server.retry.task.support.RetryTaskConverter;
import com.old.silence.job.server.retry.task.support.timer.RetryTimerContext;
import com.old.silence.job.server.retry.task.support.timer.RetryTimerTask;
import com.old.silence.job.server.retry.task.support.timer.RetryTimerWheel;

import java.time.Duration;
import java.util.Objects;


@Component
public class RetryTaskGeneratorHandler {
    private final RetryTaskDao retryTaskDao;

    public RetryTaskGeneratorHandler(RetryTaskDao retryTaskDao) {
        this.retryTaskDao = retryTaskDao;
    }

    /**
     * 生成重试任务
     *
     * @param generator RetryTaskGeneratorContext
     */
    public void generateRetryTask(RetryTaskGeneratorDTO generator) {

        RetryTask retryTask = RetryTaskConverter.INSTANCE.toRetryTask(generator);
        var taskStatus = generator.getTaskStatus();
        if (Objects.isNull(taskStatus)) {
            taskStatus = RetryTaskStatus.WAITING;
        }
        retryTask.setTaskStatus(taskStatus);
        retryTask.setOperationReason(generator.getOperationReason());

        retryTask.setExtAttrs(StrUtil.EMPTY);
        Assert.isTrue(1 == retryTaskDao.insert(retryTask), () -> new SilenceJobServerException("插入重试任务失败"));

        if (!RetryTaskStatus.WAITING.equals(taskStatus)) {
            return;
        }

        // 放到到时间轮
        long delay = generator.getNextTriggerAt() - DateUtils.toNowMilli();
        RetryTimerContext timerContext = RetryTaskConverter.INSTANCE.toRetryTimerContext(generator);
        timerContext.setRetryTaskId(retryTask.getId());
        RetryTimerWheel.registerWithRetry(() -> new RetryTimerTask(timerContext), Duration.ofMillis(delay));
    }
}
