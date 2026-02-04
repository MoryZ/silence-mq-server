package com.old.silence.job.server.retry.task.dto;


import com.old.silence.job.common.enums.RetryOperationReason;
import com.old.silence.job.common.enums.RetryTaskStatus;
import com.old.silence.job.common.enums.SystemTaskType;


public class RetryTaskGeneratorDTO extends BaseDTO {

    private RetryTaskStatus taskStatus;

    private RetryOperationReason operationReason;

    private SystemTaskType taskType;

    private long nextTriggerAt;

    private Integer retryTaskExecutorScene;

    public RetryTaskStatus getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(RetryTaskStatus taskStatus) {
        this.taskStatus = taskStatus;
    }

    public RetryOperationReason getOperationReason() {
        return operationReason;
    }

    public void setOperationReason(RetryOperationReason operationReason) {
        this.operationReason = operationReason;
    }

    @Override
    public SystemTaskType getTaskType() {
        return taskType;
    }

    @Override
    public void setTaskType(SystemTaskType taskType) {
        this.taskType = taskType;
    }

    public long getNextTriggerAt() {
        return nextTriggerAt;
    }

    public void setNextTriggerAt(long nextTriggerAt) {
        this.nextTriggerAt = nextTriggerAt;
    }

    public Integer getRetryTaskExecutorScene() {
        return retryTaskExecutorScene;
    }

    public void setRetryTaskExecutorScene(Integer retryTaskExecutorScene) {
        this.retryTaskExecutorScene = retryTaskExecutorScene;
    }
}
