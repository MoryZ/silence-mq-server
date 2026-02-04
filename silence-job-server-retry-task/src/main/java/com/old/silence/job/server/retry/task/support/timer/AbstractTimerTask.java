package com.old.silence.job.server.retry.task.support.timer;

import io.netty.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.old.silence.job.server.common.TimerTask;

import java.math.BigInteger;
import java.time.Instant;



public abstract class AbstractTimerTask implements TimerTask<String> {


    private static final Logger log = LoggerFactory.getLogger(AbstractTimerTask.class);
    protected String groupName;
    protected String namespaceId;
    protected BigInteger retryId;
    protected BigInteger retryTaskId;

    @Override
    public void run(Timeout timeout) throws Exception {
        log.debug("开始执行重试任务. 当前时间:[{}] groupName:[{}] retryId:[{}] retryTaskId:[{}] namespaceId:[{}]",
                Instant.now(), groupName, retryId, retryTaskId, namespaceId);
        try {
            doRun(timeout);
        } catch (Exception e) {
            log.error("重试任务执行失败 groupName:[{}] retryId:[{}] retryTaskId:[{}] namespaceId:[{}]",
                    groupName, retryId, retryTaskId, namespaceId, e);
        } finally {
            // 先清除时间轮的缓存
            RetryTimerWheel.removeCache(idempotentKey());

        }
    }

    protected abstract void doRun(Timeout timeout);
}
