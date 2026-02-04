package com.old.silence.job.server.job.task.support.timer;

import io.netty.util.Timeout;
import org.apache.pekko.actor.ActorRef;
import com.old.silence.job.common.enums.JobTaskExecutorScene;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.TimerTask;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.job.task.dto.JobTaskPrepareDTO;
import com.old.silence.job.server.job.task.dto.JobTimerTaskDTO;
import com.old.silence.job.server.job.task.support.JobTaskConverter;
import com.old.silence.job.server.common.pekko.ActorGenerator;

import java.text.MessageFormat;


public class ResidentJobTimerTask implements TimerTask<String> {
    private static final String IDEMPOTENT_KEY_PREFIX = " resident_job_{0}";

    private final JobTimerTaskDTO jobTimerTaskDTO;
    private final Job job;

    public ResidentJobTimerTask(JobTimerTaskDTO jobTimerTaskDTO, Job job) {
        this.jobTimerTaskDTO = jobTimerTaskDTO;
        this.job = job;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        try {
            // 清除时间轮的缓存
            JobTimerWheel.removeCache(idempotentKey());
            JobTaskPrepareDTO jobTaskPrepare = JobTaskConverter.INSTANCE.toJobTaskPrepare(job);
            jobTaskPrepare.setTaskExecutorScene(JobTaskExecutorScene.AUTO_JOB);
            // 执行预处理阶段
            ActorRef actorRef = ActorGenerator.jobTaskPrepareActor();
            actorRef.tell(jobTaskPrepare, actorRef);
        } catch (Exception e) {
            SilenceJobLog.LOCAL.error("任务调度执行失败", e);
        }
    }

    @Override
    public String idempotentKey() {
        return MessageFormat.format(IDEMPOTENT_KEY_PREFIX, jobTimerTaskDTO.getTaskBatchId());
    }
}
