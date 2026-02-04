package com.old.silence.job.server.job.task.support.generator.task;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Lists;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.enums.JobArgsType;
import com.old.silence.job.common.enums.JobTaskStatus;
import com.old.silence.job.common.enums.JobTaskType;
import com.old.silence.job.common.enums.MapReduceStage;
import com.old.silence.job.common.exception.SilenceJobMapReduceException;
import com.old.silence.job.common.model.JobArgsHolder;
import com.old.silence.job.common.util.StreamUtils;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.allocate.client.ClientLoadBalanceManager;
import com.old.silence.job.server.common.dto.RegisterNodeInfo;
import com.old.silence.job.server.common.handler.ClientNodeAllocateHandler;
import com.old.silence.job.server.common.triple.Pair;
import com.old.silence.job.server.common.util.ClientInfoUtils;
import com.old.silence.job.server.domain.model.JobTask;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.JobTaskDao;
import com.old.silence.job.server.job.task.dto.MapReduceArgsStrDTO;
import com.old.silence.job.server.job.task.support.JobTaskConverter;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 生成Map Reduce任务
 */
@Component
public class MapReduceTaskGenerator extends AbstractJobTaskGenerator {

    private static final String MERGE_REDUCE_TASK = "MERGE_REDUCE_TASK";
    private static final String REDUCE_TASK = "REDUCE_TASK";
    private final TransactionTemplate transactionTemplate;
    private final ClientNodeAllocateHandler clientNodeAllocateHandler;

    protected MapReduceTaskGenerator(JobTaskDao jobTaskDao,
                                     TransactionTemplate transactionTemplate, ClientNodeAllocateHandler clientNodeAllocateHandler) {
        super(jobTaskDao);
        this.transactionTemplate = transactionTemplate;
        this.clientNodeAllocateHandler = clientNodeAllocateHandler;
    }


    @Override
    public JobTaskType getTaskInstanceType() {
        return JobTaskType.MAP_REDUCE;
    }

    @Override
    protected List<JobTask> doGenerate(final JobTaskGenerateContext context) {
        MapReduceStage mapReduceStage = context.getMrStage();
        Assert.notNull(mapReduceStage, () -> new SilenceJobServerException("Map reduce stage is not existed"));

        List<JobTask> jobTasks;
        switch (Objects.requireNonNull(mapReduceStage)) {
            case MAP:
                // MAP任务
                jobTasks = createMapJobTasks(context);
                break;

            case REDUCE:
                // REDUCE任务
                jobTasks = createReduceJobTasks(context);
                break;

            case MERGE_REDUCE:
                // REDUCE任务
                jobTasks = createMergeReduceJobTasks(context);
                break;

            default:
                throw new SilenceJobServerException("Map reduce stage is not existed");
        }
        return jobTasks;
    }

    private List<JobTask> createMergeReduceJobTasks(JobTaskGenerateContext context) {

        List<JobTask> jobTasks = jobTaskDao.selectList(new LambdaQueryWrapper<JobTask>()
                .select(JobTask::getResultMessage)
                .eq(JobTask::getTaskBatchId, context.getTaskBatchId())
                .eq(JobTask::getMrStage, MapReduceStage.REDUCE)
                .eq(JobTask::getLeaf, true)
        );

        MapReduceArgsStrDTO jobParams = getJobParams(context);
        Pair<String, JobTaskStatus> clientInfo = getClientNodeInfo(context);
        // 新增任务实例
        JobTask jobTask = JobTaskConverter.INSTANCE.toJobTaskInstance(context);
        jobTask.setClientInfo(clientInfo.getKey());
        jobTask.setArgsType(context.getArgsType());
        JobArgsHolder jobArgsHolder = new JobArgsHolder();
        jobArgsHolder.setJobParams(jobParams.getArgsStr());
        jobArgsHolder.setReduces(StreamUtils.toList(jobTasks, JobTask::getResultMessage));
        jobTask.setArgsStr(JSON.toJSONString(jobArgsHolder));
        jobTask.setTaskStatus(clientInfo.getValue());
        jobTask.setResultMessage(Optional.ofNullable(jobTask.getResultMessage()).orElse(StrUtil.EMPTY));
        jobTask.setMrStage(MapReduceStage.MERGE_REDUCE);
        jobTask.setTaskName(MERGE_REDUCE_TASK);
        Assert.isTrue(1 == jobTaskDao.insert(jobTask),
                () -> new SilenceJobServerException("新增任务实例失败"));

        return Lists.newArrayList(jobTask);
    }

    private List<JobTask> createReduceJobTasks(JobTaskGenerateContext context) {

        MapReduceArgsStrDTO jobParams = getJobParams(context);
        int reduceParallel = Math.max(1,
                Optional.ofNullable(jobParams.getShardNum()).orElse(1));

        List<JobTask> jobTasks = jobTaskDao.selectList(new LambdaQueryWrapper<JobTask>()
                .select(JobTask::getResultMessage, JobTask::getId)
                .eq(JobTask::getTaskBatchId, context.getTaskBatchId())
                .eq(JobTask::getMrStage, MapReduceStage.MAP)
                .eq(JobTask::getLeaf, true)
        );

        if (CollectionUtils.isEmpty(jobTasks)) {
            return Lists.newArrayList();
        }

        // 这里需要判断是否是map
        List<String> allMapJobTasks = StreamUtils.toList(jobTasks, JobTask::getResultMessage);
        List<List<String>> partition = averageAlgorithm(allMapJobTasks, reduceParallel);

        jobTasks = new ArrayList<>(partition.size());
        final List<JobTask> finalJobTasks = jobTasks;
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(final TransactionStatus status) {
                for (int index = 0; index < partition.size(); index++) {
                    Pair<String, JobTaskStatus> clientInfo = getClientNodeInfo(context);

                    // 新增任务实例
                    JobTask jobTask = JobTaskConverter.INSTANCE.toJobTaskInstance(context);
                    jobTask.setClientInfo(clientInfo.getKey());
                    jobTask.setArgsType(context.getArgsType());
                    JobArgsHolder jobArgsHolder = new JobArgsHolder();
                    jobArgsHolder.setJobParams(jobParams.getArgsStr());
                    jobArgsHolder.setMaps(partition.get(index));
                    jobTask.setArgsStr(JSON.toJSONString(jobArgsHolder));
                    jobTask.setTaskStatus(clientInfo.getValue());
                    jobTask.setResultMessage(Optional.ofNullable(jobTask.getResultMessage()).orElse(StrUtil.EMPTY));
                    jobTask.setMrStage(MapReduceStage.REDUCE);
                    jobTask.setTaskName(REDUCE_TASK);
                    jobTask.setParentId(BigInteger.ZERO);
                    jobTask.setRetryCount(0);
                    jobTask.setLeaf(true);
                    jobTask.setCreatedDate(Instant.now());
                    jobTask.setUpdatedDate(Instant.now());
                    finalJobTasks.add(jobTask);
                }

                batchSaveJobTasks(finalJobTasks);
            }
        });

        return finalJobTasks;
    }

    private List<JobTask> createMapJobTasks(final JobTaskGenerateContext context) {
        List<?> mapSubTask = context.getMapSubTask();
        if (CollectionUtils.isEmpty(mapSubTask)) {
            SilenceJobLog.LOCAL.warn("Map sub task is empty. TaskBatchId:[{}]", context.getTaskBatchId());
            return Lists.newArrayList();
        }

        MapReduceArgsStrDTO jobParams = getJobParams(context);

        // 判定父节点是不是叶子节点，若是则不更新否则更新为非叶子节点
        JobTask parentJobTask = jobTaskDao.selectOne(
                new LambdaQueryWrapper<JobTask>()
                        .select(JobTask::getId)
                        .eq(JobTask::getId, Optional.ofNullable(context.getParentId()).orElse(BigInteger.ZERO))
                        .eq(JobTask::getLeaf, true)
        );

        return transactionTemplate.execute(status -> {
            List<JobTask> jobTasks = new ArrayList<>(mapSubTask.size());
            for (int index = 0; index < mapSubTask.size(); index++) {
                Pair<String, JobTaskStatus> clientInfo = getClientNodeInfo(context);

                // 新增任务实例
                JobTask jobTask = JobTaskConverter.INSTANCE.toJobTaskInstance(context);
                jobTask.setClientInfo(clientInfo.getKey());
                jobTask.setArgsType(context.getArgsType());
                JobArgsHolder jobArgsHolder = new JobArgsHolder();
                jobArgsHolder.setJobParams(jobParams.getArgsStr());
                jobArgsHolder.setMaps(mapSubTask.get(index));
                jobTask.setArgsStr(JSON.toJSONString(jobArgsHolder));
                jobTask.setArgsType(JobArgsType.JSON);
                jobTask.setTaskStatus(clientInfo.getValue());
                jobTask.setMrStage(MapReduceStage.MAP);
                jobTask.setTaskName(context.getTaskName());
                jobTask.setLeaf(true);
                jobTask.setParentId(Objects.isNull(context.getParentId()) ? BigInteger.ZERO : context.getParentId());
                jobTask.setRetryCount(0);
                jobTask.setCreatedDate(Instant.now());
                jobTask.setUpdatedDate(Instant.now());
                jobTask.setResultMessage(Optional.ofNullable(jobTask.getResultMessage()).orElse(StrUtil.EMPTY));
                jobTasks.add(jobTask);
            }

            batchSaveJobTasks(jobTasks);

            // 更新父节点的为非叶子节点
            if (Objects.nonNull(parentJobTask)) {
                JobTask parentJobTask1 = new JobTask();
                parentJobTask1.setId(context.getParentId());
                parentJobTask1.setLeaf(false);
                Assert.isTrue(1 == super.jobTaskDao.updateById(parentJobTask1),
                        () -> new SilenceJobMapReduceException("更新父节点失败"));
            }

            return jobTasks;
        });

    }

    protected MapReduceArgsStrDTO getJobParams(JobTaskGenerateContext context) {
        try {
            return JSON.parseObject(context.getArgsStr(), MapReduceArgsStrDTO.class);
        } catch (Exception e) {
            SilenceJobLog.LOCAL.error("map reduce args parse error. argsStr:[{}]", context.getArgsStr());
        }

        return new MapReduceArgsStrDTO();
    }

    private Pair<String, JobTaskStatus> getClientNodeInfo(JobTaskGenerateContext context) {
        RegisterNodeInfo serverNode = clientNodeAllocateHandler.getServerNode(
                context.getJobId().toString(),
                context.getGroupName(),
                context.getNamespaceId(),
                ClientLoadBalanceManager.AllocationAlgorithmEnum.ROUND.getType()
        );
        String clientInfo = StrUtil.EMPTY;
        JobTaskStatus jobTaskStatus = JobTaskStatus.RUNNING;
        if (Objects.isNull(serverNode)) {
            jobTaskStatus = JobTaskStatus.CANCEL;
        } else {
            clientInfo = ClientInfoUtils.generate(serverNode);
        }

        return Pair.of(clientInfo, jobTaskStatus);
    }

    private List<List<String>> averageAlgorithm(List<String> allMapJobTasks, int shard) {

        // 最多分片数为allMapJobTasks.size()
        shard = Math.min(allMapJobTasks.size(), shard);
        int totalSize = allMapJobTasks.size();
        List<Integer> partitionSizes = new ArrayList<>();
        int quotient = totalSize / shard;
        int remainder = totalSize % shard;

        for (int i = 0; i < shard; i++) {
            partitionSizes.add(quotient + (i < remainder ? 1 : 0));
        }

        List<List<String>> partitions = new ArrayList<>();
        int currentIndex = 0;

        for (int size : partitionSizes) {
            int endIndex = Math.min(currentIndex + size, totalSize);
            partitions.add(new ArrayList<>(allMapJobTasks.subList(currentIndex, endIndex)));
            currentIndex = endIndex;
        }

        return partitions;
    }
}
