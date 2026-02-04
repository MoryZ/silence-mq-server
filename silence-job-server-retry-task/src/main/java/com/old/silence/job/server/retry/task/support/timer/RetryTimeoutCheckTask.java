package com.old.silence.job.server.retry.task.support.timer;

import io.netty.util.Timeout;
import com.old.silence.job.common.enums.RetryOperationReason;
import com.old.silence.job.common.enums.RetryTaskStatus;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.TimerTask;
import com.old.silence.job.server.domain.model.Retry;
import com.old.silence.job.server.domain.model.RetryTask;
import com.old.silence.job.server.infrastructure.persistence.dao.RetryDao;
import com.old.silence.job.server.infrastructure.persistence.dao.RetryTaskDao;
import com.old.silence.job.server.retry.task.dto.TaskStopJobDTO;
import com.old.silence.job.server.retry.task.support.RetryTaskConverter;
import com.old.silence.job.server.retry.task.support.handler.RetryTaskStopHandler;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Objects;

/**
 * 任务超时检查
 *
 */
public class RetryTimeoutCheckTask implements TimerTask<String> {
    private static final String IDEMPOTENT_KEY_PREFIX = "retry_timeout_check_{0}";

    private final BigInteger retryTaskId;
    private final BigInteger retryId;
    private final RetryTaskStopHandler retryTaskStopHandler;
    private final RetryDao retryDao;
    private final RetryTaskDao retryTaskDao;

    public RetryTimeoutCheckTask(BigInteger retryTaskId, BigInteger retryId, RetryTaskStopHandler retryTaskStopHandler, RetryDao retryDao, RetryTaskDao retryTaskDao) {
        this.retryTaskId = retryTaskId;
        this.retryId = retryId;
        this.retryTaskStopHandler = retryTaskStopHandler;
        this.retryDao = retryDao;
        this.retryTaskDao = retryTaskDao;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        RetryTimerWheel.removeCache(idempotentKey());
        RetryTask retryTask = retryTaskDao.selectById(retryTaskId);
        if (Objects.isNull(retryTask)) {
            SilenceJobLog.LOCAL.error("retryTaskId:[{}] 不存在", retryTaskId);
            return;
        }

        // 已经完成了，无需重复停止任务
        if (RetryTaskStatus.TERMINAL_STATUS_SET.contains(retryTask.getTaskStatus())) {
            return;
        }

        Retry retry = retryDao.selectById(retryId);
        if (Objects.isNull(retry)) {
            SilenceJobLog.LOCAL.error("retryId:[{}]不存在", retryId);
            return;
        }

        // 超时停止任务
        String reason = "超时中断.retryTaskId:[" + retryTaskId + "]";

        TaskStopJobDTO stopJobDTO = RetryTaskConverter.INSTANCE.toTaskStopJobDTO(retry);
        stopJobDTO.setRetryTaskId(retryTaskId);
        stopJobDTO.setRetryId(retryId);
        stopJobDTO.setOperationReason(RetryOperationReason.TASK_EXECUTION_TIMEOUT);
        stopJobDTO.setNeedUpdateTaskStatus(true);
        retryTaskStopHandler.stop(stopJobDTO);

        SilenceJobLog.LOCAL.info(reason);
    }

    @Override
    public String idempotentKey() {
        return MessageFormat.format(IDEMPOTENT_KEY_PREFIX, retryId);
    }
}
