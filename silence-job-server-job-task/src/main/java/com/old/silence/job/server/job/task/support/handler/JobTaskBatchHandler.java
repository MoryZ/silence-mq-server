package com.old.silence.job.server.job.task.support.handler;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.common.enums.JobTaskExecutorScene;
import com.old.silence.job.common.enums.JobTaskType;
import com.old.silence.job.common.enums.MapReduceStage;
import com.old.silence.job.common.model.JobArgsHolder;
import com.old.silence.job.server.common.WaitStrategy;
import com.old.silence.job.server.common.dto.DistributeInstance;
import com.old.silence.job.server.common.strategy.WaitStrategies;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.domain.model.JobTask;
import com.old.silence.job.server.domain.model.JobTaskBatch;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.GroupConfigDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobTaskBatchDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobTaskDao;
import com.old.silence.job.server.job.task.dto.CompleteJobBatchDTO;
import com.old.silence.job.server.job.task.dto.JobTimerTaskDTO;
import com.old.silence.job.server.job.task.dto.MapReduceArgsStrDTO;
import com.old.silence.job.server.job.task.dto.TaskExecuteDTO;
import com.old.silence.job.server.job.task.support.JobExecutorResultHandler;
import com.old.silence.job.server.job.task.support.JobTaskConverter;
import com.old.silence.job.server.job.task.support.cache.ResidentTaskCache;
import com.old.silence.job.server.job.task.support.result.job.JobExecutorResultContext;
import com.old.silence.job.server.job.task.support.timer.JobTimerWheel;
import com.old.silence.job.server.job.task.support.timer.ResidentJobTimerTask;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static com.old.silence.job.common.enums.JobTaskBatchStatus.COMPLETED;


@Component

public class JobTaskBatchHandler {


    private static final Logger log = LoggerFactory.getLogger(JobTaskBatchHandler.class);
    private final JobTaskBatchDao jobTaskBatchDao;
    private final JobTaskDao jobTaskDao;
    private final GroupConfigDao groupConfigDao;
    private final List<JobExecutorResultHandler> jobExecutorResultHandlers;

    public JobTaskBatchHandler(JobTaskBatchDao jobTaskBatchDao, JobTaskDao jobTaskDao,
                               GroupConfigDao groupConfigDao, List<JobExecutorResultHandler> jobExecutorResultHandlers) {
        this.jobTaskBatchDao = jobTaskBatchDao;
        this.jobTaskDao = jobTaskDao;
        this.groupConfigDao = groupConfigDao;
        this.jobExecutorResultHandlers = jobExecutorResultHandlers;
    }

    @Transactional
    public boolean handleResult(CompleteJobBatchDTO completeJobBatchDTO) {
        Assert.notNull(completeJobBatchDTO.getTaskType(), () -> new SilenceJobServerException("taskType can not be null"));
        Assert.notNull(completeJobBatchDTO.getRetryStatus(), () -> new SilenceJobServerException("retryStatus can not be null"));

        // 非重试流量幂等处理
        if (Boolean.FALSE.equals(completeJobBatchDTO.getRetryStatus())) {
            // 幂等处理
            Long countJobTaskBatch = jobTaskBatchDao.selectCount(new LambdaQueryWrapper<JobTaskBatch>()
                    .eq(JobTaskBatch::getId, completeJobBatchDTO.getTaskBatchId())
                    .in(JobTaskBatch::getTaskBatchStatus, COMPLETED)
            );
            if (countJobTaskBatch > 0) {
                // 批次已经完成了，不需要重复更新
                return true;
            }
        }

        JobExecutorResultContext context = JobTaskConverter.INSTANCE.toJobExecutorResultContext(completeJobBatchDTO);
        for (JobExecutorResultHandler jobExecutorResultHandler : jobExecutorResultHandlers) {
            if (completeJobBatchDTO.getTaskType().equals(jobExecutorResultHandler.getTaskInstanceType())) {
                jobExecutorResultHandler.handleResult(context);
                break;
            }
        }

        // 处理的结果 若已经更新成功 或者 需要开启reduce任务都算是已经处理了
        return context.isTaskBatchComplete() || context.isCreateReduceTask();
    }

    /**
     * 开启常驻任务
     *
     * @param job            定时任务配置信息
     * @param taskExecuteDTO 任务执行新
     */
    public void openResidentTask(Job job, TaskExecuteDTO taskExecuteDTO) {
        if (Objects.isNull(job)
                || !job.getJobStatus()
                || JobTaskExecutorScene.MANUAL_JOB.equals(taskExecuteDTO.getTaskExecutorScene())
                || JobTaskExecutorScene.AUTO_WORKFLOW.equals(taskExecuteDTO.getTaskExecutorScene())
                || JobTaskExecutorScene.MANUAL_WORKFLOW.equals(taskExecuteDTO.getTaskExecutorScene())
                // 是否是常驻任务
                || Objects.equals(500, job.getResident())
                // 防止任务已经分配到其他节点导致的任务重复执行
                || !DistributeInstance.INSTANCE.getConsumerBucket().contains(job.getBucketIndex())
        ) {
            return;
        }

        long count = groupConfigDao.selectCount(new LambdaQueryWrapper<GroupConfig>()
                .eq(GroupConfig::getNamespaceId, job.getNamespaceId())
                .eq(GroupConfig::getGroupName, job.getGroupName())
                .eq(GroupConfig::getGroupStatus, true));
        if (count == 0) {
            return;
        }

        JobTimerTaskDTO jobTimerTaskDTO = new JobTimerTaskDTO();
        jobTimerTaskDTO.setJobId(taskExecuteDTO.getJobId());
        jobTimerTaskDTO.setTaskBatchId(taskExecuteDTO.getTaskBatchId());
        jobTimerTaskDTO.setTaskExecutorScene(JobTaskExecutorScene.AUTO_JOB);
        WaitStrategy waitStrategy = WaitStrategies.WaitStrategyEnum.getWaitStrategy(job.getTriggerType().getValue());

        Long preTriggerAt = ResidentTaskCache.get(job.getId());
        if (Objects.isNull(preTriggerAt) || preTriggerAt < job.getNextTriggerAt()) {
            preTriggerAt = job.getNextTriggerAt();
        }

        WaitStrategies.WaitStrategyContext waitStrategyContext = new WaitStrategies.WaitStrategyContext();
        waitStrategyContext.setTriggerInterval(job.getTriggerInterval());
        waitStrategyContext.setNextTriggerAt(preTriggerAt);
        Long nextTriggerAt = waitStrategy.computeTriggerTime(waitStrategyContext);

        // 获取时间差的毫秒数
        long milliseconds = nextTriggerAt - preTriggerAt;

        Duration duration = Duration.ofMillis(milliseconds - DateUtils.toNowMilli() % 1000);

        log.debug("常驻任务监控. [{}] 任务时间差:[{}] 取余:[{}]", duration, milliseconds,
                DateUtils.toNowMilli() % 1000);
        job.setNextTriggerAt(nextTriggerAt);
        JobTimerWheel.registerWithJob(() -> new ResidentJobTimerTask(jobTimerTaskDTO, job), duration);
        ResidentTaskCache.refresh(job.getId(), nextTriggerAt);
    }

    /**
     * 这里为了兼容MAP或者MAP_REDUCE场景下手动执行任务的时候参数丢失问题，
     * 需要从JobTask中获取任务类型为MAP的且是taskName是ROOT_MAP的任务的参数作为执行参数下发给客户端
     *
     * @param taskBatchId 任务批次
     * @param job         任务
     * @return 需要给客户端下发的参数
     */
    public String getArgStr(BigInteger taskBatchId, Job job) {
        JobTask rootMapTask = jobTaskDao.selectList(
                new LambdaQueryWrapper<JobTask>()
                        .select(JobTask::getId, JobTask::getArgsStr)
                        .eq(JobTask::getTaskBatchId, taskBatchId)
                        .eq(JobTask::getMrStage, MapReduceStage.MAP.getValue())
                        .eq(JobTask::getTaskName, SystemConstants.ROOT_MAP)
                        .orderByAsc(JobTask::getId)
        ).stream().findFirst().orElse(null);

        // {"jobParams":"测试参数传递","maps":""}
        String argsStr = job.getArgsStr();
        if (Objects.nonNull(rootMapTask) && StrUtil.isNotBlank(rootMapTask.getArgsStr())) {
            JobArgsHolder jobArgsHolder = JSON.parseObject(rootMapTask.getArgsStr(), JobArgsHolder.class);
            // MAP_REDUCE的参数: {"shardNum":2,"argsStr":"测试参数传递"} 这里得解析出来覆盖argsStr
            if (JobTaskType.MAP_REDUCE.equals(job.getTaskType())) {

                MapReduceArgsStrDTO mapReduceArgsStrDTO = JSON.parseObject(argsStr, MapReduceArgsStrDTO.class);
                mapReduceArgsStrDTO.setArgsStr((String) jobArgsHolder.getJobParams());
                argsStr = JSON.toJSONString(mapReduceArgsStrDTO);
            } else {
                // MAP的参数: 测试参数传递 直接覆盖即可
                argsStr = (String) jobArgsHolder.getJobParams();
            }
        }

        return argsStr;
    }

}
