package com.old.silence.job.server.job.task.support.timer;

import io.netty.util.Timeout;
import org.apache.pekko.actor.ActorRef;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.TimerTask;
import com.old.silence.job.server.job.task.dto.RealJobExecutorDTO;
import com.old.silence.job.server.common.pekko.ActorGenerator;

import java.text.MessageFormat;
import java.time.Instant;

public class RetryJobTimerTask implements TimerTask<String> {
    public static final String IDEMPOTENT_KEY_PREFIX = "retry_job_{0}";
    private final RealJobExecutorDTO jobExecutorDTO;

    public RetryJobTimerTask(RealJobExecutorDTO jobExecutorDTO) {
        this.jobExecutorDTO = jobExecutorDTO;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        // 执行任务调度
        SilenceJobLog.LOCAL.debug("开始执行重试任务调度. 当前时间:[{}] taskId:[{}]", Instant.now(), jobExecutorDTO.getTaskBatchId());
        JobTimerWheel.removeCache(idempotentKey());
        try {
            ActorRef actorRef = ActorGenerator.jobRealTaskExecutorActor();
            actorRef.tell(jobExecutorDTO, actorRef);
        } catch (Exception e) {
            SilenceJobLog.LOCAL.error("重试任务调度执行失败", e);
        }
    }

    @Override
    public String idempotentKey() {
        return MessageFormat.format(IDEMPOTENT_KEY_PREFIX, jobExecutorDTO.getTaskId());
    }
}
