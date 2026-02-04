package com.old.silence.job.server.job.task.support.prepare.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.old.silence.job.common.enums.JobTaskBatchStatus;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.job.task.dto.JobTaskPrepareDTO;
import com.old.silence.job.server.job.task.dto.JobTimerTaskDTO;
import com.old.silence.job.server.job.task.support.timer.JobTimerTask;
import com.old.silence.job.server.job.task.support.timer.JobTimerWheel;

import java.text.MessageFormat;
import java.time.Duration;

/**
 * 处理处于{@link JobTaskBatchStatus ::WAIT}状态的任务
 */
@Component

public class WaitJobPrepareHandler extends AbstractJobPrepareHandler {


    private static final Logger log = LoggerFactory.getLogger(WaitJobPrepareHandler.class);

    @Override
    public boolean matches(JobTaskBatchStatus status) {
        return JobTaskBatchStatus.WAITING == status;
    }

    @Override
    protected void doHandle(JobTaskPrepareDTO jobPrepareDTO) {
        log.debug("存在待处理任务. taskBatchId:[{}]", jobPrepareDTO.getTaskBatchId());

        // 若时间轮中数据不存在则重新加入
        if (!JobTimerWheel.checkExisted(MessageFormat.format(JobTimerTask.IDEMPOTENT_KEY_PREFIX, jobPrepareDTO.getTaskBatchId()))) {
            log.info("存在待处理任务且时间轮中不存在 taskBatchId:[{}]", jobPrepareDTO.getTaskBatchId());

            // 进入时间轮
            long delay = jobPrepareDTO.getNextTriggerAt() - DateUtils.toNowMilli();
            JobTimerTaskDTO jobTimerTaskDTO = new JobTimerTaskDTO();
            jobTimerTaskDTO.setTaskBatchId(jobPrepareDTO.getTaskBatchId());
            jobTimerTaskDTO.setJobId(jobPrepareDTO.getJobId());

            JobTimerWheel.registerWithJob(() -> new JobTimerTask(jobTimerTaskDTO), Duration.ofMillis(delay));
        }
    }

}
