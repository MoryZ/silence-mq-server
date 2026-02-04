package com.old.silence.job.server.retry.task.support.schedule;

import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.enums.RetryStatus;
import com.old.silence.job.common.util.StreamUtils;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.Lifecycle;
import com.old.silence.job.server.common.config.SystemProperties;
import com.old.silence.job.server.common.dto.PartitionTask;
import com.old.silence.job.server.common.schedule.AbstractSchedule;
import com.old.silence.job.server.common.triple.Triple;
import com.old.silence.job.server.common.util.PartitionTaskUtils;
import com.old.silence.job.server.domain.model.RetryTask;
import com.old.silence.job.server.domain.model.RetryTaskLogMessage;
import com.old.silence.job.server.infrastructure.persistence.dao.RetryTaskDao;
import com.old.silence.job.server.infrastructure.persistence.dao.RetryTaskLogMessageDao;
import com.old.silence.job.server.retry.task.dto.RetryMergePartitionTaskDTO;
import com.old.silence.job.server.retry.task.support.RetryTaskLogConverter;
import com.old.silence.job.server.task.common.schedule.LogMergeUtils;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * jogLogMessage 日志合并归档
 *
 */

@Component
public class RetryLogMergeSchedule extends AbstractSchedule implements Lifecycle {

    private final SystemProperties systemProperties;
    private final RetryTaskDao retryTaskDao;
    private final RetryTaskLogMessageDao retryTaskLogMessageDao;
    private final TransactionTemplate transactionTemplate;

    public RetryLogMergeSchedule(SystemProperties systemProperties, RetryTaskDao retryTaskDao,
                                 RetryTaskLogMessageDao retryTaskLogMessageDao, TransactionTemplate transactionTemplate) {
        this.systemProperties = systemProperties;
        this.retryTaskDao = retryTaskDao;
        this.retryTaskLogMessageDao = retryTaskLogMessageDao;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public String lockName() {
        return "retryLogMerge";
    }

    @Override
    public String lockAtMost() {
        return "PT1H";
    }

    @Override
    public String lockAtLeast() {
        return "PT1M";
    }

    @Override
    protected void doExecute() {
        try {
            // merge job log
            long total;
            Instant endTime = Instant.now().minus(systemProperties.getMergeLogDays(), ChronoUnit.DAYS);
            total = PartitionTaskUtils.process(startId -> retryLogList(startId, endTime),
                    this::processJobLogPartitionTasks, 0);

            SilenceJobLog.LOCAL.debug("job merge success total:[{}]", total);
        } catch (Exception e) {
            SilenceJobLog.LOCAL.error("job merge log error", e);
        }
    }

    /**
     * JobLog List
     *
     */
    private List<RetryMergePartitionTaskDTO> retryLogList(Long startId, Instant endTime) {

        List<RetryTask> jobTaskBatchList = retryTaskDao.selectPage(
                new Page<>(0, 1000),
                new LambdaUpdateWrapper<RetryTask>()
                        .ge(RetryTask::getId, startId)
                        .in(RetryTask::getTaskStatus, List.of(
                                RetryStatus.FINISH,
                                RetryStatus.MAX_COUNT))
                        .le(RetryTask::getCreatedDate, endTime)
                        .orderByAsc(RetryTask::getId)
        ).getRecords();
        return RetryTaskLogConverter.INSTANCE.toRetryMergePartitionTaskDTOs(jobTaskBatchList);
    }

    /**
     * merge job_log_message
     *
     */
    public void processJobLogPartitionTasks(List<? extends PartitionTask> partitionTasks) {

        // Waiting for merge RetryTaskLog
        List<BigInteger> ids = StreamUtils.toList(partitionTasks, PartitionTask::getId);
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        // Waiting for deletion RetryTaskLogMessage
        List<RetryTaskLogMessage> retryLogMessageList = retryTaskLogMessageDao.selectList(
                new LambdaQueryWrapper<RetryTaskLogMessage>()
                        .in(RetryTaskLogMessage::getRetryTaskId, ids));
        if (CollectionUtils.isEmpty(retryLogMessageList)) {
            return;
        }

        List<Map.Entry<Triple<String, String, BigInteger>, List<RetryTaskLogMessage>>> jobLogMessageGroupList = retryLogMessageList.stream()
                .collect(
                        groupingBy(message -> Triple.of(message.getNamespaceId(), message.getGroupName(),
                                message.getRetryTaskId())))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2).toList();

        for (Map.Entry<Triple<String, String, BigInteger>/*taskId*/, List<RetryTaskLogMessage>> jobLogMessageMap : jobLogMessageGroupList) {
            
            // 使用工具类合并日志
            LogMergeUtils.MergeResult mergeResult = LogMergeUtils.mergeLogs(
                    jobLogMessageMap.getValue(),
                    RetryTaskLogMessage::getMessage,
                    RetryTaskLogMessage::getId
            );
            
            List<BigInteger> jobLogMessageDeleteBatchIds = mergeResult.getDeleteIds();

            // 分区并创建新日志记录
            List<List<String>> partitionMessages = LogMergeUtils.partitionMessages(
                    mergeResult.getMergedMessages(),
                    systemProperties.getMergeLogNum()
            );

            List<RetryTaskLogMessage> jobLogMessageUpdateList = new ArrayList<>();

            for (int i = 0; i < partitionMessages.size(); i++) {
                // 深拷贝
                RetryTaskLogMessage jobLogMessage = RetryTaskLogConverter.INSTANCE.toRetryTaskLogMessage(
                        jobLogMessageMap.getValue().get(0));
                List<String> messages = partitionMessages.get(i);

                jobLogMessage.setLogNum(messages.size());
                jobLogMessage.setMessage(JSON.toJSONString(messages));
                jobLogMessageUpdateList.add(jobLogMessage);
            }

            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(final TransactionStatus status) {
                    // 批量删除、更新日志
                    if (CollectionUtils.isNotEmpty(jobLogMessageDeleteBatchIds)) {
                        retryTaskLogMessageDao.deleteBatchIds(jobLogMessageDeleteBatchIds);
                    }
                    if (CollectionUtils.isNotEmpty(jobLogMessageUpdateList)) {
                        retryTaskLogMessageDao.insertBatch(jobLogMessageUpdateList);
                    }
                }
            });
        }
    }

    @Override
    public void start() {
        taskScheduler.scheduleAtFixedRate(this::execute, Duration.parse("PT1H"));
    }

    @Override
    public void close() {

    }
}
