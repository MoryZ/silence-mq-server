package com.old.silence.job.server.job.task.support.timer;

import io.netty.util.Timeout;
import com.old.silence.job.common.context.SilenceSpringContext;
import com.old.silence.job.common.enums.JobNotifyScene;
import com.old.silence.job.common.enums.JobOperationReason;
import com.old.silence.job.common.enums.JobTaskBatchStatus;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.TimerTask;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.domain.model.JobTaskBatch;
import com.old.silence.job.server.infrastructure.persistence.dao.JobDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobTaskBatchDao;
import com.old.silence.job.server.job.task.dto.JobTaskFailAlarmEventDTO;
import com.old.silence.job.server.job.task.support.JobTaskConverter;
import com.old.silence.job.server.job.task.support.JobTaskStopHandler;
import com.old.silence.job.server.job.task.support.alarm.event.JobTaskFailAlarmEvent;
import com.old.silence.job.server.job.task.support.stop.JobTaskStopFactory;
import com.old.silence.job.server.job.task.support.stop.TaskStopJobContext;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Objects;


public class JobTimeoutCheckTask implements TimerTask<String> {
    private static final String IDEMPOTENT_KEY_PREFIX = "job_timeout_check_{0}";

    private final BigInteger taskBatchId;
    private final BigInteger jobId;

    public JobTimeoutCheckTask(BigInteger taskBatchId, BigInteger jobId) {
        this.taskBatchId = taskBatchId;
        this.jobId = jobId;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        JobTimerWheel.removeCache(idempotentKey());
        JobTaskBatchDao jobTaskBatchDao = SilenceSpringContext.getBean(JobTaskBatchDao.class);
        JobTaskBatch jobTaskBatch = jobTaskBatchDao.selectById(taskBatchId);
        if (Objects.isNull(jobTaskBatch)) {
            SilenceJobLog.LOCAL.error("jobTaskBatch:[{}]不存在", taskBatchId);
            return;
        }

        // 已经完成了，无需重复停止任务
        if (JobTaskBatchStatus.COMPLETED.contains(jobTaskBatch.getTaskBatchStatus())) {
            return;
        }

        JobDao jobMapper = SilenceSpringContext.getBean(JobDao.class);
        Job job = jobMapper.selectById(jobId);
        if (Objects.isNull(job)) {
            SilenceJobLog.LOCAL.error("job:[{}]不存在", jobId);
            return;
        }

        // 超时停止任务
        JobTaskStopHandler instanceInterrupt = JobTaskStopFactory.getJobTaskStop(job.getTaskType());
        TaskStopJobContext stopJobContext = JobTaskConverter.INSTANCE.toStopJobContext(job);
        stopJobContext.setJobOperationReason(JobOperationReason.TASK_EXECUTION_TIMEOUT);
        stopJobContext.setNeedUpdateTaskStatus(Boolean.TRUE);
        stopJobContext.setForceStop(Boolean.TRUE);
        stopJobContext.setTaskBatchId(taskBatchId);
        stopJobContext.setWorkflowNodeId(jobTaskBatch.getWorkflowNodeId());
        stopJobContext.setWorkflowTaskBatchId(jobTaskBatch.getWorkflowTaskBatchId());
        instanceInterrupt.stop(stopJobContext);

        String reason = "超时中断.taskBatchId:[" + taskBatchId + "]";
        var jobTaskFailAlarmEventDTO = new JobTaskFailAlarmEventDTO();
        jobTaskFailAlarmEventDTO.setJobTaskBatchId(taskBatchId);
        jobTaskFailAlarmEventDTO.setReason(JobNotifyScene.JOB_NO_CLIENT_NODES_ERROR.getDescription());
        jobTaskFailAlarmEventDTO.setNotifyScene(JobNotifyScene.JOB_NO_CLIENT_NODES_ERROR);
        SilenceSpringContext.getContext().publishEvent(
                new JobTaskFailAlarmEvent(jobTaskFailAlarmEventDTO));
        SilenceJobLog.LOCAL.info(reason);
    }

    @Override
    public String idempotentKey() {
        return MessageFormat.format(IDEMPOTENT_KEY_PREFIX, taskBatchId);
    }
}
