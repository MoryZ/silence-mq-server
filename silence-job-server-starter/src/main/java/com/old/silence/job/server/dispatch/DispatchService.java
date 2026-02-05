package com.old.silence.job.server.dispatch;

import org.apache.pekko.actor.ActorRef;
import org.springframework.stereotype.Component;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.Lifecycle;
import com.old.silence.job.server.common.dto.DistributeInstance;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.common.pekko.ActorGenerator;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 负责SilenceJob系统任务分发调度
 *
 */
@Component

public class DispatchService implements Lifecycle {

    /**
     * 分配器线程
     */
    private final ScheduledExecutorService dispatchService = Executors
            .newSingleThreadScheduledExecutor(r -> new Thread(r, "dispatch-service"));

    /**
     * 调度时长
     */
    public static final Long PERIOD = SystemConstants.SCHEDULE_PERIOD;

    /**
     * 延迟30s为了尽可能保障集群节点都启动完成在进行re balance
     */
    public static final Long INITIAL_DELAY = SystemConstants.SCHEDULE_INITIAL_DELAY;

    @Override
    public void start() {

        // TODO待优化
        ActorRef actorRef = ActorGenerator.scanBucketActor();

        dispatchService.scheduleAtFixedRate(() -> {

            try {
                // 当正在re balance时延迟10s，尽量等待所有节点都完成re balance
                if (DistributeInstance.RE_BALANCE_ING.get()) {
                    SilenceJobLog.LOCAL.info("正在rebalance中....");
                    TimeUnit.SECONDS.sleep(INITIAL_DELAY);
                }

                Set<Integer> currentConsumerBuckets = getConsumerBucket();
                if (CollectionUtils.isNotEmpty(currentConsumerBuckets)) {
                    ConsumerBucket scanTaskDTO = new ConsumerBucket();
                    scanTaskDTO.setBuckets(currentConsumerBuckets);
                    actorRef.tell(scanTaskDTO, actorRef);
                }

            } catch (Exception e) {
                SilenceJobLog.LOCAL.error("分发异常", e);
            }


        }, INITIAL_DELAY, PERIOD, TimeUnit.SECONDS);
    }


    /**
     * 分配当前POD负责消费的桶
     *
     * @return {@link  GroupConfig} 组上下文
     */
    private Set<Integer> getConsumerBucket() {
        return DistributeInstance.INSTANCE.getConsumerBucket();
    }

    @Override
    public void close() {

    }
}
