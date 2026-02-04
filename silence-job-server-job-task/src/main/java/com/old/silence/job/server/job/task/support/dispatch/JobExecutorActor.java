package com.old.silence.job.server.job.task.support.dispatch;

import cn.hutool.core.lang.Assert;
import org.apache.pekko.actor.AbstractActor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.common.context.SilenceSpringContext;
import com.old.silence.job.common.enums.JobNotifyScene;
import com.old.silence.job.common.enums.JobOperationReason;
import com.old.silence.job.common.enums.JobTaskBatchStatus;
import com.old.silence.job.common.enums.JobTaskExecutorScene;
import com.old.silence.job.common.enums.MapReduceStage;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.cache.CacheRegisterTable;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.domain.model.JobTask;
import com.old.silence.job.server.domain.model.JobTaskBatch;
import com.old.silence.job.server.domain.model.WorkflowTaskBatch;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.JobDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobTaskBatchDao;
import com.old.silence.job.server.infrastructure.persistence.dao.WorkflowTaskBatchDao;
import com.old.silence.job.server.job.task.dto.JobTaskFailAlarmEventDTO;
import com.old.silence.job.server.job.task.dto.TaskExecuteDTO;
import com.old.silence.job.server.job.task.dto.WorkflowNodeTaskExecuteDTO;
import com.old.silence.job.server.job.task.support.JobExecutor;
import com.old.silence.job.server.job.task.support.JobTaskConverter;
import com.old.silence.job.server.job.task.support.alarm.event.JobTaskFailAlarmEvent;
import com.old.silence.job.server.job.task.support.executor.job.JobExecutorContext;
import com.old.silence.job.server.job.task.support.executor.job.JobExecutorFactory;
import com.old.silence.job.server.job.task.support.generator.task.JobTaskGenerateContext;
import com.old.silence.job.server.job.task.support.generator.task.JobTaskGenerator;
import com.old.silence.job.server.job.task.support.generator.task.JobTaskGeneratorFactory;
import com.old.silence.job.server.job.task.support.handler.JobTaskBatchHandler;
import com.old.silence.job.server.job.task.support.handler.WorkflowBatchHandler;
import com.old.silence.job.server.job.task.support.timer.JobTimeoutCheckTask;
import com.old.silence.job.server.job.task.support.timer.JobTimerTask;
import com.old.silence.job.server.job.task.support.timer.JobTimerWheel;
import com.old.silence.job.server.common.pekko.ActorGenerator;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import cn.hutool.core.util.StrUtil;
import static com.old.silence.job.common.enums.JobTaskType.MAP;
import static com.old.silence.job.common.enums.JobTaskType.MAP_REDUCE;


@Component(ActorGenerator.JOB_EXECUTOR_ACTOR)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)

public class JobExecutorActor extends AbstractActor {


    private static final Logger log = LoggerFactory.getLogger(JobExecutorActor.class);
    private final JobDao jobDao;
    private final JobTaskBatchDao jobTaskBatchDao;
    private final TransactionTemplate transactionTemplate;
    private final WorkflowBatchHandler workflowBatchHandler;
    private final JobTaskBatchHandler jobTaskBatchHandler;
    private final WorkflowTaskBatchDao workflowTaskBatchDao;

    public JobExecutorActor(JobDao jobDao, JobTaskBatchDao jobTaskBatchDao, TransactionTemplate transactionTemplate,
                            WorkflowBatchHandler workflowBatchHandler, JobTaskBatchHandler jobTaskBatchHandler,
                            WorkflowTaskBatchDao workflowTaskBatchDao) {
        this.jobDao = jobDao;
        this.jobTaskBatchDao = jobTaskBatchDao;
        this.transactionTemplate = transactionTemplate;
        this.workflowBatchHandler = workflowBatchHandler;
        this.jobTaskBatchHandler = jobTaskBatchHandler;
        this.workflowTaskBatchDao = workflowTaskBatchDao;
    }

    @NotNull
    private static JobExecutorContext buildJobExecutorContext(TaskExecuteDTO taskExecute, Job job, List<JobTask> taskList,
                                                              final WorkflowTaskBatch workflowTaskBatch) {
        JobExecutorContext context = JobTaskConverter.INSTANCE.toJobExecutorContext(job);
        context.setTaskList(taskList);
        context.setTaskBatchId(taskExecute.getTaskBatchId());
        context.setJobId(job.getId());
        context.setWorkflowTaskBatchId(taskExecute.getWorkflowTaskBatchId());
        context.setWorkflowNodeId(taskExecute.getWorkflowNodeId());
        if (Objects.nonNull(workflowTaskBatch)) {
            context.setWfContext(workflowTaskBatch.getWfContext());
        }
        return context;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(TaskExecuteDTO.class, taskExecute -> {
            try {
                log.debug("准备执行任务. [{}] [{}]", Instant.now(), JSON.toJSONString(taskExecute));

                transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(final TransactionStatus status) {
                        doExecute(taskExecute);
                    }
                });

            } catch (Exception e) {
                SilenceJobLog.LOCAL.error("job executor exception. [{}]", taskExecute, e);
                handleTaskBatch(taskExecute, JobTaskBatchStatus.FAIL, JobOperationReason.TASK_EXECUTION_ERROR);
            } finally {
                getContext().stop(getSelf());
            }
        }).build();
    }

    private void doExecute(final TaskExecuteDTO taskExecute) {

        LambdaQueryWrapper<Job> queryWrapper = new LambdaQueryWrapper<>();
        // 自动地校验任务必须是开启状态，手动触发无需校验
        if (JobTaskExecutorScene.AUTO_JOB.equals(taskExecute.getTaskExecutorScene())) {
            queryWrapper.eq(Job::getJobStatus, true);
        }

        Job job = jobDao.selectOne(queryWrapper.eq(Job::getId, taskExecute.getJobId()));
        JobTaskBatchStatus taskStatus = JobTaskBatchStatus.RUNNING;
        try {
            JobOperationReason operationReason = JobOperationReason.NONE;
            if (Objects.isNull(job)) {
                taskStatus = JobTaskBatchStatus.CANCEL;
                operationReason = JobOperationReason.JOB_CLOSED;
            } else if (CollectionUtils.isEmpty(CacheRegisterTable.getServerNodeSet(job.getGroupName(), job.getNamespaceId()))) {
                taskStatus = JobTaskBatchStatus.CANCEL;
                operationReason = JobOperationReason.NOT_CLIENT;

                WorkflowNodeTaskExecuteDTO taskExecuteDTO = new WorkflowNodeTaskExecuteDTO();
                taskExecuteDTO.setWorkflowTaskBatchId(taskExecute.getWorkflowTaskBatchId());
                taskExecuteDTO.setTaskExecutorScene(taskExecute.getTaskExecutorScene());
                taskExecuteDTO.setParentId(taskExecute.getWorkflowNodeId());
                taskExecuteDTO.setTaskBatchId(taskExecute.getTaskBatchId());
                workflowBatchHandler.openNextNode(taskExecuteDTO);
            }

            // 无客户端节点-告警通知
            if (CollectionUtils.isEmpty(CacheRegisterTable.getServerNodeSet(job.getGroupName(), job.getNamespaceId()))) {
                var jobTaskFailAlarmEventDTO = new JobTaskFailAlarmEventDTO();

                jobTaskFailAlarmEventDTO.setJobTaskBatchId(taskExecute.getTaskBatchId());
                jobTaskFailAlarmEventDTO.setReason(JobNotifyScene.JOB_NO_CLIENT_NODES_ERROR.getDescription());
                jobTaskFailAlarmEventDTO.setNotifyScene(JobNotifyScene.JOB_NO_CLIENT_NODES_ERROR);
                SilenceSpringContext.getContext().publishEvent(
                        new JobTaskFailAlarmEvent(jobTaskFailAlarmEventDTO));
                return;
            }

            // 更新状态
            handleTaskBatch(taskExecute, taskStatus, operationReason);

            // 不是运行中的，不需要生产任务
            if (taskStatus != JobTaskBatchStatus.RUNNING) {
                return;
            }

            // 生成任务
            JobTaskGenerator taskInstance = JobTaskGeneratorFactory.getTaskInstance(job.getTaskType());
            JobTaskGenerateContext instanceGenerateContext = JobTaskConverter.INSTANCE.toJobTaskInstanceGenerateContext(job);
            instanceGenerateContext.setTaskBatchId(taskExecute.getTaskBatchId());
            if (Objects.nonNull(taskExecute.getTmpArgsStr())) {
                instanceGenerateContext.setArgsStr(taskExecute.getTmpArgsStr());
            }
            if (List.of(MAP_REDUCE, MAP).contains(job.getTaskType())) {
                instanceGenerateContext.setTaskName(SystemConstants.ROOT_MAP);
                instanceGenerateContext.setMapSubTask(List.of(StrUtil.EMPTY));
                instanceGenerateContext.setMrStage(MapReduceStage.MAP);
            }
            List<JobTask> taskList = taskInstance.generate(instanceGenerateContext);
            if (CollectionUtils.isEmpty(taskList)) {
                SilenceJobLog.LOCAL.warn("Generate job task is empty, taskBatchId:[{}]", taskExecute.getTaskBatchId());
                return;
            }

            // 事务提交以后再执行任务
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 获取工作流的上下文
                    WorkflowTaskBatch workflowTaskBatch = null;
                    BigInteger workflowTaskBatchId = taskExecute.getWorkflowTaskBatchId();
                    if (Objects.nonNull(workflowTaskBatchId)) {
                        workflowTaskBatch = workflowTaskBatchDao.selectOne(
                                new LambdaQueryWrapper<WorkflowTaskBatch>()
                                        .select(WorkflowTaskBatch::getWfContext)
                                        .eq(WorkflowTaskBatch::getId, taskExecute.getWorkflowTaskBatchId())
                        );
                    }

                    // 执行任务
                    JobExecutor jobExecutor = JobExecutorFactory.getJobExecutor(job.getTaskType());
                    jobExecutor.execute(buildJobExecutorContext(taskExecute, job, taskList, workflowTaskBatch));
                }
            });

        } finally {
            log.debug("准备执行任务完成.[{}]", JSON.toJSONString(taskExecute));
            JobTaskBatchStatus finalTaskStatus = taskStatus;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    // 清除时间轮的缓存
                    JobTimerWheel.removeCache(MessageFormat.format(JobTimerTask.IDEMPOTENT_KEY_PREFIX, taskExecute.getTaskBatchId()));

                    if (JobTaskBatchStatus.RUNNING == finalTaskStatus) {

                        // 运行中的任务，需要进行超时检查
                        JobTimerWheel.registerWithJob(() -> new JobTimeoutCheckTask(taskExecute.getTaskBatchId(), job.getId()),
                                // 加500ms是为了让尽量保证客户端自己先超时中断，防止客户端上报成功但是服务端已触发超时中断
                                Duration.ofMillis(DateUtils.toEpochMilli(job.getExecutorTimeout()) + 500));
                    }

                    // 开启下一个常驻任务
                    jobTaskBatchHandler.openResidentTask(job, taskExecute);
                }
            });
        }

    }

    private void handleTaskBatch(TaskExecuteDTO taskExecute, JobTaskBatchStatus taskStatus, JobOperationReason operationReason) {

        JobTaskBatch jobTaskBatch = new JobTaskBatch();
        jobTaskBatch.setId(taskExecute.getTaskBatchId());
        jobTaskBatch.setExecutionAt(DateUtils.toNowMilli());
        jobTaskBatch.setTaskBatchStatus(taskStatus);
        jobTaskBatch.setOperationReason(operationReason);
        Assert.isTrue(1 == jobTaskBatchDao.updateById(jobTaskBatch),
                () -> new SilenceJobServerException("更新任务失败"));

        if (JobTaskBatchStatus.NOT_SUCCESS.contains(taskStatus)) {
            var jobTaskFailAlarmEventDTO = new JobTaskFailAlarmEventDTO();
            jobTaskFailAlarmEventDTO.setJobTaskBatchId(taskExecute.getTaskBatchId());
            jobTaskFailAlarmEventDTO.setReason(JobOperationReason.TASK_EXECUTION_ERROR.getDescription());
            jobTaskFailAlarmEventDTO.setNotifyScene(JobNotifyScene.JOB_TASK_ERROR);
            SilenceSpringContext.getContext().publishEvent(
                    new JobTaskFailAlarmEvent(jobTaskFailAlarmEventDTO));
        }
    }

}
