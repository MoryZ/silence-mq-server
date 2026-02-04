package com.old.silence.job.server.job.task.support.timer;

import io.netty.util.Timeout;
import com.old.silence.job.common.context.SilenceSpringContext;
import com.old.silence.job.common.enums.JobNotifyScene;
import com.old.silence.job.common.enums.JobOperationReason;
import com.old.silence.job.common.enums.JobTaskBatchStatus;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.TimerTask;
import com.old.silence.job.server.domain.model.WorkflowTaskBatch;
import com.old.silence.job.server.infrastructure.persistence.dao.WorkflowTaskBatchDao;
import com.old.silence.job.server.job.task.dto.WorkflowTaskFailAlarmEventDTO;
import com.old.silence.job.server.job.task.support.alarm.event.WorkflowTaskFailAlarmEvent;
import com.old.silence.job.server.job.task.support.handler.WorkflowBatchHandler;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Objects;


public class WorkflowTimeoutCheckTask implements TimerTask<String> {
    private static final String IDEMPOTENT_KEY_PREFIX = "workflow_timeout_check_{0}";

    private final BigInteger workflowTaskBatchId;

    public WorkflowTimeoutCheckTask(BigInteger workflowTaskBatchId) {
        this.workflowTaskBatchId = workflowTaskBatchId;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        JobTimerWheel.removeCache(idempotentKey());
        WorkflowTaskBatchDao workflowTaskBatchDao = SilenceSpringContext.getBean(WorkflowTaskBatchDao.class);
        WorkflowTaskBatch workflowTaskBatch = workflowTaskBatchDao.selectById(workflowTaskBatchId);
        // 幂等检查
        if (Objects.isNull(workflowTaskBatch) || JobTaskBatchStatus.COMPLETED.contains(workflowTaskBatch.getTaskBatchStatus())) {
            return;
        }

        WorkflowBatchHandler workflowBatchHandler = SilenceSpringContext.getBean(WorkflowBatchHandler.class);

        // 超时停止任务
        workflowBatchHandler.stop(workflowTaskBatchId, JobOperationReason.TASK_EXECUTION_TIMEOUT);

        String reason = String.format("超时中断.workflowTaskBatchId:[%s]", workflowTaskBatchId);
        var workflowTaskFailAlarmEventDTO = new WorkflowTaskFailAlarmEventDTO();
        workflowTaskFailAlarmEventDTO.setWorkflowTaskBatchId(workflowTaskBatchId);
        workflowTaskFailAlarmEventDTO.setNotifyScene(JobNotifyScene.WORKFLOW_TASK_ERROR);
        workflowTaskFailAlarmEventDTO.setReason(reason);

        SilenceSpringContext.getContext().publishEvent(new WorkflowTaskFailAlarmEvent(workflowTaskFailAlarmEventDTO));

        SilenceJobLog.LOCAL.info(reason);
    }

    @Override
    public String idempotentKey() {
        return MessageFormat.format(IDEMPOTENT_KEY_PREFIX, workflowTaskBatchId);
    }
}
