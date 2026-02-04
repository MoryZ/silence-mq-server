package com.old.silence.job.server.job.task.support.prepare.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.old.silence.job.common.enums.JobTaskBatchStatus;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.job.task.dto.WorkflowTaskPrepareDTO;
import com.old.silence.job.server.job.task.dto.WorkflowTimerTaskDTO;
import com.old.silence.job.server.job.task.support.timer.JobTimerWheel;
import com.old.silence.job.server.job.task.support.timer.WorkflowTimerTask;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;

/**
 * 处理处于{@link JobTaskBatchStatus ::WAIT}状态的任务
 */
@Component

public class WaitWorkflowPrepareHandler extends AbstractWorkflowPrePareHandler {


    private static final Logger log = LoggerFactory.getLogger(WaitWorkflowPrepareHandler.class);

    @Override
    public boolean matches(JobTaskBatchStatus status) {
        return Objects.nonNull(status) && JobTaskBatchStatus.WAITING == status;
    }

    @Override
    protected void doHandler(WorkflowTaskPrepareDTO workflowTaskPrepareDTO) {
        log.debug("存在待处理任务. workflowTaskBatchId:[{}]", workflowTaskPrepareDTO.getWorkflowTaskBatchId());

        // 若时间轮中数据不存在则重新加入
        if (!JobTimerWheel.checkExisted(MessageFormat.format(WorkflowTimerTask.IDEMPOTENT_KEY_PREFIX, workflowTaskPrepareDTO.getWorkflowTaskBatchId()))) {
            log.info("存在待处理任务且时间轮中不存在 workflowTaskBatchId:[{}]", workflowTaskPrepareDTO.getWorkflowTaskBatchId());

            // 进入时间轮
            long delay = workflowTaskPrepareDTO.getNextTriggerAt() - DateUtils.toNowMilli();
            WorkflowTimerTaskDTO workflowTimerTaskDTO = new WorkflowTimerTaskDTO();
            workflowTimerTaskDTO.setWorkflowTaskBatchId(workflowTaskPrepareDTO.getWorkflowTaskBatchId());
            workflowTimerTaskDTO.setWorkflowId(workflowTaskPrepareDTO.getWorkflowId());
            workflowTimerTaskDTO.setTaskExecutorScene(workflowTaskPrepareDTO.getTaskExecutorScene());

            JobTimerWheel.registerWithWorkflow(() -> new WorkflowTimerTask(workflowTimerTaskDTO), Duration.ofMillis(delay));
        }
    }
}
