package com.old.silence.job.server.handler;

import cn.hutool.core.lang.Assert;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.pekko.actor.ActorRef;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.client.dto.ExecuteResult;
import com.old.silence.job.common.enums.JobOperationReason;
import com.old.silence.job.common.enums.JobTaskBatchStatus;
import com.old.silence.job.common.enums.JobTaskExecutorScene;
import com.old.silence.job.common.enums.JobTaskStatus;
import com.old.silence.job.server.common.pekko.ActorGenerator;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.domain.model.JobLogMessage;
import com.old.silence.job.server.domain.model.JobTask;
import com.old.silence.job.server.domain.model.JobTaskBatch;
import com.old.silence.job.server.domain.model.WorkflowTaskBatch;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.JobDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobLogMessageDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobTaskBatchDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobTaskDao;
import com.old.silence.job.server.infrastructure.persistence.dao.WorkflowTaskBatchDao;
import com.old.silence.job.server.job.task.dto.TaskExecuteDTO;
import com.old.silence.job.server.job.task.enums.JobRetrySceneEnum;
import com.old.silence.job.server.job.task.support.ClientCallbackHandler;
import com.old.silence.job.server.job.task.support.JobTaskConverter;
import com.old.silence.job.server.job.task.support.JobTaskStopHandler;
import com.old.silence.job.server.job.task.support.callback.ClientCallbackContext;
import com.old.silence.job.server.job.task.support.callback.ClientCallbackFactory;
import com.old.silence.job.server.job.task.support.stop.JobTaskStopFactory;
import com.old.silence.job.server.job.task.support.stop.TaskStopJobContext;
import com.old.silence.job.server.job.task.support.timer.JobTimeoutCheckTask;
import com.old.silence.job.server.job.task.support.timer.JobTimerWheel;


@Component

public class JobHandler {

    private final JobTaskBatchDao jobTaskBatchDao;
    private final JobDao jobDao;
    private final JobTaskDao jobTaskDao;
    private final WorkflowTaskBatchDao workflowTaskBatchDao;
    private final JobLogMessageDao jobLogMessageDao;

    public JobHandler(JobTaskBatchDao jobTaskBatchDao, JobDao jobDao,
                      JobTaskDao jobTaskDao, WorkflowTaskBatchDao workflowTaskBatchDao,
                      JobLogMessageDao jobLogMessageDao) {
        this.jobTaskBatchDao = jobTaskBatchDao;
        this.jobDao = jobDao;
        this.jobTaskDao = jobTaskDao;
        this.workflowTaskBatchDao = workflowTaskBatchDao;
        this.jobLogMessageDao = jobLogMessageDao;
    }

    public Boolean retry(BigInteger taskBatchId) {
        return retry(taskBatchId, null, null);
    }

    public Boolean retry(BigInteger taskBatchId, BigInteger workflowNodeId, BigInteger workflowTaskBatchId) {
        JobTaskBatch jobTaskBatch = jobTaskBatchDao.selectOne(new LambdaQueryWrapper<JobTaskBatch>()
                .eq(JobTaskBatch::getId, taskBatchId)
                .in(JobTaskBatch::getTaskBatchStatus, JobTaskBatchStatus.NOT_SUCCESS)
        );
        Assert.notNull(jobTaskBatch, () -> new SilenceJobServerException("job batch can not be null."));

        // 重置状态为运行中
        jobTaskBatch.setTaskBatchStatus(JobTaskBatchStatus.RUNNING);
        // 重置状态原因
        jobTaskBatch.setOperationReason(JobOperationReason.NONE);

        UpdateWrapper<JobTaskBatch> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskBatchId);

        var updateJobTaskBatch = new JobTaskBatch();
        updateJobTaskBatch.setTaskBatchStatus(JobTaskBatchStatus.RUNNING);
        updateJobTaskBatch.setOperationReason(JobOperationReason.NONE);
        Assert.isTrue(jobTaskBatchDao.update(updateJobTaskBatch, wrapper) > 0,
                () -> new SilenceJobServerException("update job batch to running failed."));

        Job job = jobDao.selectById(jobTaskBatch.getJobId());
        Assert.notNull(job, () -> new SilenceJobServerException("job can not be null."));

        List<JobTask> jobTasks = jobTaskDao.selectList(
                new LambdaQueryWrapper<JobTask>()
                        .select(JobTask::getId, JobTask::getTaskStatus)
                        .eq(JobTask::getTaskBatchId, taskBatchId));

        //  若任务项为空则生成
        if (CollectionUtils.isEmpty(jobTasks)) {
            TaskExecuteDTO taskExecuteDTO = new TaskExecuteDTO();
            taskExecuteDTO.setTaskBatchId(taskBatchId);
            taskExecuteDTO.setJobId(jobTaskBatch.getJobId());
            taskExecuteDTO.setTaskExecutorScene(JobTaskExecutorScene.MANUAL_JOB);
            taskExecuteDTO.setWorkflowTaskBatchId(workflowTaskBatchId);
            taskExecuteDTO.setWorkflowNodeId(workflowNodeId);
            ActorRef actorRef = ActorGenerator.jobTaskExecutorActor();
            actorRef.tell(taskExecuteDTO, actorRef);

            return Boolean.TRUE;
        }

        // 获取工作流上下文
        String wfContext = getWfContext(workflowTaskBatchId);

        for (JobTask jobTask : jobTasks) {
            // 增加Map及MapReduce重试任务的状态判断，防止重复执行
            if (jobTask.getTaskStatus() == JobTaskStatus.RUNNING
                    || jobTask.getTaskStatus() == JobTaskStatus.SUCCESS) {
                continue;
            }

            jobTask.setTaskStatus(JobTaskStatus.RUNNING);
            Assert.isTrue(jobTaskDao.updateById(jobTask) > 0,
                    () -> new SilenceJobServerException("update job task to running failed."));
            // 模拟失败重试
            ClientCallbackHandler clientCallback = ClientCallbackFactory.getClientCallback(job.getTaskType());
            ClientCallbackContext context = JobTaskConverter.INSTANCE.toClientCallbackContext(job);
            context.setTaskBatchId(jobTaskBatch.getId());
            context.setWorkflowNodeId(workflowNodeId);
            context.setWorkflowTaskBatchId(workflowTaskBatchId);
            context.setTaskId(jobTask.getId());
            context.setTaskStatus(JobTaskStatus.FAIL);
            context.setRetryScene(JobRetrySceneEnum.MANUAL.getRetryScene());
            context.setWfContext(wfContext);
            context.setExecuteResult(ExecuteResult.failure(null, "手动重试"));
            clientCallback.callback(context);
        }

        // 运行中的任务，需要进行超时检查
        JobTimerWheel.registerWithJob(() -> new JobTimeoutCheckTask(taskBatchId, job.getId()),
                // 加500ms是为了让尽量保证客户端自己先超时中断，防止客户端上报成功但是服务端已触发超时中断
                Duration.ofMillis(DateUtils.toEpochMilli(job.getExecutorTimeout()) + 500));

        return Boolean.TRUE;
    }

    public Boolean stop(BigInteger taskBatchId) {

        JobTaskBatch jobTaskBatch = jobTaskBatchDao.selectById(taskBatchId);
        Assert.notNull(jobTaskBatch, () -> new SilenceJobServerException("job batch can not be null."));

        Job job = jobDao.selectById(jobTaskBatch.getJobId());
        Assert.notNull(job, () -> new SilenceJobServerException("job can not be null."));

        JobTaskStopHandler jobTaskStop = JobTaskStopFactory.getJobTaskStop(job.getTaskType());

        TaskStopJobContext taskStopJobContext = JobTaskConverter.INSTANCE.toStopJobContext(job);
        taskStopJobContext.setJobOperationReason(JobOperationReason.MANNER_STOP);
        taskStopJobContext.setTaskBatchId(jobTaskBatch.getId());
        taskStopJobContext.setForceStop(Boolean.TRUE);
        taskStopJobContext.setNeedUpdateTaskStatus(Boolean.TRUE);

        jobTaskStop.stop(taskStopJobContext);

        return Boolean.TRUE;
    }

    /**
     * 获取工作流批次
     *
     * @param workflowTaskBatchId 工作流批次
     * @return
     */
    private String getWfContext(BigInteger workflowTaskBatchId) {
        if (Objects.isNull(workflowTaskBatchId)) {
            return null;
        }

        WorkflowTaskBatch workflowTaskBatch = workflowTaskBatchDao.selectOne(
                new LambdaQueryWrapper<WorkflowTaskBatch>()
                        .select(WorkflowTaskBatch::getWfContext)
                        .eq(WorkflowTaskBatch::getId, workflowTaskBatchId)
        );

        if (Objects.isNull(workflowTaskBatch)) {
            return null;
        }

        return workflowTaskBatch.getWfContext();
    }

    /**
     * 批次删除定时任务批次
     *
     * @param ids         任务批次id
     */
    @Transactional
    public void deleteJobTaskBatchByIds(Set<BigInteger> ids) {
        // 1. 删除任务批次 job_task_batch
        Assert.isTrue(ids.size() == jobTaskBatchDao.deleteBatchIds(ids),
                () -> new SilenceJobServerException("删除任务批次失败"));

        // 2. 删除任务实例 job_task
        jobTaskDao.delete(new LambdaQueryWrapper<JobTask>()
                .in(JobTask::getTaskBatchId, ids));

        // 3. 删除调度日志 job_log_message
        jobLogMessageDao.delete(new LambdaQueryWrapper<JobLogMessage>()
                .in(JobLogMessage::getTaskBatchId, ids)
        );
    }
}
