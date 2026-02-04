package com.old.silence.job.server.job.task.support.dispatch;

import cn.hutool.core.lang.Assert;
import org.apache.pekko.actor.AbstractActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Sets;
import com.google.common.graph.MutableGraph;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.common.context.SilenceSpringContext;
import com.old.silence.job.common.enums.FailStrategy;
import com.old.silence.job.common.enums.JobNotifyScene;
import com.old.silence.job.common.enums.JobOperationReason;
import com.old.silence.job.common.enums.JobTaskBatchStatus;
import com.old.silence.job.common.enums.WorkflowNodeType;
import com.old.silence.job.common.util.StreamUtils;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.domain.model.JobTaskBatch;
import com.old.silence.job.server.domain.model.Workflow;
import com.old.silence.job.server.domain.model.WorkflowNode;
import com.old.silence.job.server.domain.model.WorkflowTaskBatch;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.JobDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobTaskBatchDao;
import com.old.silence.job.server.infrastructure.persistence.dao.WorkflowDao;
import com.old.silence.job.server.infrastructure.persistence.dao.WorkflowNodeDao;
import com.old.silence.job.server.infrastructure.persistence.dao.WorkflowTaskBatchDao;
import com.old.silence.job.server.job.task.dto.WorkflowNodeTaskExecuteDTO;
import com.old.silence.job.server.job.task.dto.WorkflowTaskFailAlarmEventDTO;
import com.old.silence.job.server.job.task.support.WorkflowExecutor;
import com.old.silence.job.server.job.task.support.WorkflowTaskConverter;
import com.old.silence.job.server.job.task.support.alarm.event.WorkflowTaskFailAlarmEvent;
import com.old.silence.job.server.job.task.support.cache.MutableGraphCache;
import com.old.silence.job.server.job.task.support.executor.workflow.WorkflowExecutorContext;
import com.old.silence.job.server.job.task.support.executor.workflow.WorkflowExecutorFactory;
import com.old.silence.job.server.job.task.support.handler.WorkflowBatchHandler;
import com.old.silence.job.server.job.task.support.timer.JobTimerWheel;
import com.old.silence.job.server.job.task.support.timer.WorkflowTimeoutCheckTask;
import com.old.silence.job.server.job.task.support.timer.WorkflowTimerTask;
import com.old.silence.job.server.common.pekko.ActorGenerator;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.old.silence.job.common.enums.JobOperationReason.WORKFLOW_SUCCESSOR_SKIP_EXECUTION;


@Component(ActorGenerator.WORKFLOW_EXECUTOR_ACTOR)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)

public class WorkflowExecutorActor extends AbstractActor {


    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutorActor.class);
    private final WorkflowTaskBatchDao workflowTaskBatchDao;
    private final WorkflowNodeDao workflowNodeDao;
    private final WorkflowDao workflowDao;
    private final JobDao jobDao;
    private final JobTaskBatchDao jobTaskBatchDao;
    private final WorkflowBatchHandler workflowBatchHandler;

    public WorkflowExecutorActor(WorkflowTaskBatchDao workflowTaskBatchDao, WorkflowNodeDao workflowNodeDao,
                                 WorkflowDao workflowDao, JobDao jobDao,
                                 JobTaskBatchDao jobTaskBatchDao, WorkflowBatchHandler workflowBatchHandler) {
        this.workflowTaskBatchDao = workflowTaskBatchDao;
        this.workflowNodeDao = workflowNodeDao;
        this.workflowDao = workflowDao;
        this.jobDao = jobDao;
        this.jobTaskBatchDao = jobTaskBatchDao;
        this.workflowBatchHandler = workflowBatchHandler;
    }

    private static void fillParentOperationReason(final List<JobTaskBatch> allJobTaskBatchList,
                                                  final List<JobTaskBatch> parentJobTaskBatchList, final WorkflowNode parentWorkflowNode,
                                                  final WorkflowExecutorContext context) {
        JobTaskBatch jobTaskBatch = allJobTaskBatchList.stream()
                .filter(batch -> !WORKFLOW_SUCCESSOR_SKIP_EXECUTION.contains(batch.getOperationReason()))
                .findFirst().orElse(null);

        /*
          若当前节点的父节点存在无需处理的节点(比如决策节点的某个未匹中的分支)，则需要等待正常的节点来执行此节点，若正常节点已经调度过了，
          此时则没有能触发后继节点继续调度的节点存在了。 因此这里将改变parentOperationReason = 0使之能继续往后处理
          基于性能的考虑这里在直接在parentJobTaskBatchList列表的头节点插入一个不是跳过的节点，这样就可以正常流转了
          eg: {"-1":[480],"480":[481,488,490],"481":[482],"482":[483],"483":[484],"484":[485],"485":[486],"486":[487],"487":[497,498],"488":[489],"489":[497,498],"490":[491,493,495],"491":[492],"492":[497,498],"493":[494],"494":[497,498],"495":[496],"496":[497,498],"497":[499],"498":[499],"499":[]}
        */
        if (parentJobTaskBatchList.stream()
                .map(JobTaskBatch::getOperationReason)
                .filter(Objects::nonNull)
                .anyMatch(JobOperationReason.WORKFLOW_SUCCESSOR_SKIP_EXECUTION::contains)
                && Objects.nonNull(jobTaskBatch)
                && !parentWorkflowNode.getNodeType().equals(WorkflowNodeType.DECISION)) {

            context.setParentOperationReason(JobOperationReason.NONE);
        } else {
            context.setParentOperationReason(parentJobTaskBatchList.get(0).getOperationReason());
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(WorkflowNodeTaskExecuteDTO.class, taskExecute -> {
            log.info("工作流开始执行. [{}]", JSON.toJSONString(taskExecute));
            try {

                doExecutor(taskExecute);

            } catch (Exception e) {
                SilenceJobLog.LOCAL.error("workflow executor exception. [{}]", taskExecute, e);
                handlerTaskBatch(taskExecute,
                        JobTaskBatchStatus.FAIL,
                        JobOperationReason.TASK_EXECUTION_ERROR);

                var workflowTaskFailAlarmEventDTO = new WorkflowTaskFailAlarmEventDTO();
                workflowTaskFailAlarmEventDTO.setWorkflowTaskBatchId(taskExecute.getWorkflowTaskBatchId());
                workflowTaskFailAlarmEventDTO.setNotifyScene(JobNotifyScene.WORKFLOW_TASK_ERROR);
                workflowTaskFailAlarmEventDTO.setReason(e.getMessage());

                SilenceSpringContext.getContext().publishEvent(new WorkflowTaskFailAlarmEvent(workflowTaskFailAlarmEventDTO));
            } finally {
                getContext().stop(getSelf());
            }
        }).build();
    }

    private void doExecutor(WorkflowNodeTaskExecuteDTO taskExecute) {
        WorkflowTaskBatch workflowTaskBatch = workflowTaskBatchDao.selectById(taskExecute.getWorkflowTaskBatchId());
        Assert.notNull(workflowTaskBatch, () -> new SilenceJobServerException("任务不存在"));

        if (SystemConstants.ROOT.equals(taskExecute.getParentId())
                && JobTaskBatchStatus.WAITING == workflowTaskBatch.getTaskBatchStatus()) {
            handlerTaskBatch(taskExecute, JobTaskBatchStatus.RUNNING,
                    JobOperationReason.NONE);

            Workflow workflow = workflowDao.selectById(workflowTaskBatch.getWorkflowId());
            JobTimerWheel.removeCache(
                    MessageFormat.format(WorkflowTimerTask.IDEMPOTENT_KEY_PREFIX, taskExecute.getWorkflowTaskBatchId()));

            JobTimerWheel.registerWithWorkflow(() -> new WorkflowTimeoutCheckTask(taskExecute.getWorkflowTaskBatchId()),
                    Duration.ofSeconds(workflow.getExecutorTimeout()));
        }

        // 获取DAG图
        String flowInfo = workflowTaskBatch.getFlowInfo();
        MutableGraph<BigInteger> graph = MutableGraphCache.getOrDefault(workflowTaskBatch.getId(), flowInfo);

        Set<BigInteger> brotherNode = MutableGraphCache.getBrotherNode(graph, taskExecute.getParentId());
        Sets.SetView<BigInteger> setView = Sets.union(brotherNode, Sets.newHashSet(taskExecute.getParentId()));
        // 查到当前节点【ParentId】的所有兄弟节点是否有后继节点，若有则不能直接完成任务
        Set<BigInteger> allSuccessors = Sets.newHashSet();
        for (BigInteger nodeId : setView.immutableCopy()) {
            Set<BigInteger> successors = graph.successors(nodeId);
            if (CollectionUtils.isNotEmpty(successors)) {
                for (BigInteger successor : successors) {
                    // 寻找当前的节点的所有前序节点
                    allSuccessors.addAll(graph.predecessors(successor));
                }
                allSuccessors.addAll(successors);
            }
        }

        log.debug("父节点:[{}] 所有的节点:[{}]", taskExecute.getParentId(), allSuccessors);

        // 若所有的兄弟节点的子节点都没有后继节点可以完成次任务
        if (CollectionUtils.isEmpty(allSuccessors)) {
            workflowBatchHandler.complete(taskExecute.getWorkflowTaskBatchId(), workflowTaskBatch);
            return;
        }

        // 添加父节点，为了判断父节点的处理状态
        List<JobTaskBatch> allJobTaskBatchList = jobTaskBatchDao.selectList(new LambdaQueryWrapper<JobTaskBatch>()
                .select(JobTaskBatch::getWorkflowTaskBatchId, JobTaskBatch::getWorkflowNodeId,
                        JobTaskBatch::getTaskBatchStatus, JobTaskBatch::getOperationReason, JobTaskBatch::getId)
                .eq(JobTaskBatch::getWorkflowTaskBatchId, workflowTaskBatch.getId())
                .in(JobTaskBatch::getWorkflowNodeId,
                        Sets.union(allSuccessors, Sets.newHashSet(taskExecute.getParentId())))
        );

        List<WorkflowNode> workflowNodes = workflowNodeDao.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .in(WorkflowNode::getId, Sets.union(allSuccessors, Sets.newHashSet(taskExecute.getParentId())))
                .orderByAsc(WorkflowNode::getPriorityLevel));

        Map<BigInteger, List<JobTaskBatch>> jobTaskBatchMap = StreamUtils.groupByKey(allJobTaskBatchList,
                JobTaskBatch::getWorkflowNodeId);
        Map<BigInteger, WorkflowNode> workflowNodeMap = StreamUtils.toIdentityMap(workflowNodes, WorkflowNode::getId);
        List<JobTaskBatch> parentJobTaskBatchList = jobTaskBatchMap.get(taskExecute.getParentId());

        WorkflowNode parentWorkflowNode = workflowNodeMap.get(taskExecute.getParentId());

        // 决策节点
        if (Objects.nonNull(parentWorkflowNode)
                && WorkflowNodeType.DECISION.equals(parentWorkflowNode.getNodeType())) {

            // 获取决策节点子节点
            Set<BigInteger> successors = graph.successors(parentWorkflowNode.getId());
            workflowNodes = workflowNodes.stream()
                    // 去掉父节点
                    .filter(workflowNode -> !workflowNode.getId().equals(taskExecute.getParentId())
                            // 过滤掉非当前决策节点【ParentId】的子节点
                            && successors.contains(workflowNode.getId())).collect(Collectors.toList());
        } else {
            workflowNodes = workflowNodes.stream()
                    // 去掉父节点
                    .filter(workflowNode -> !workflowNode.getId().equals(taskExecute.getParentId()))
                    .collect(Collectors.toList());

            // 此次的并发数与当时父节点的兄弟节点的数量一致
            workflowBatchHandler.mergeWorkflowContextAndRetry(workflowTaskBatch,
                    StreamUtils.toSet(allJobTaskBatchList, JobTaskBatch::getId));
        }

        List<Job> jobs = jobDao.selectBatchIds(StreamUtils.toSet(workflowNodes, WorkflowNode::getJobId));
        Map<BigInteger, Job> jobMap = StreamUtils.toIdentityMap(jobs, Job::getId);

        // 只会条件节点会使用
        Object evaluationResult = null;
        log.debug("待执行的节点为. workflowNodes:[{}]", StreamUtils.toList(workflowNodes, WorkflowNode::getId));
        for (WorkflowNode workflowNode : workflowNodes) {

            // 批次已经存在就不在重复生成
            List<JobTaskBatch> jobTaskBatchList = jobTaskBatchMap.get(workflowNode.getId());
            if (CollectionUtils.isNotEmpty(jobTaskBatchList)) {
                continue;
            }

            // 决策当前节点要不要执行
            Set<BigInteger> predecessors = graph.predecessors(workflowNode.getId());
            boolean predecessorsComplete = arePredecessorsComplete(taskExecute, predecessors, jobTaskBatchMap,
                    workflowNode, workflowNodeMap);
            if (!SystemConstants.ROOT.equals(taskExecute.getParentId()) && !predecessorsComplete) {
                continue;
            }

            // 执行DAG中的节点
            WorkflowExecutor workflowExecutor = WorkflowExecutorFactory.getWorkflowExecutor(workflowNode.getNodeType());

            WorkflowExecutorContext context = WorkflowTaskConverter.INSTANCE.toWorkflowExecutorContext(workflowNode);
            context.setJob(jobMap.get(workflowNode.getJobId()));
            context.setWorkflowTaskBatchId(taskExecute.getWorkflowTaskBatchId());
            context.setParentWorkflowNodeId(taskExecute.getParentId());
            context.setEvaluationResult(evaluationResult);
            context.setTaskBatchId(taskExecute.getTaskBatchId());
            context.setTaskExecutorScene(taskExecute.getTaskExecutorScene());
            context.setWfContext(workflowTaskBatch.getWfContext());
            // 这里父节点取最新的批次判断状态
            if (CollectionUtils.isNotEmpty(parentJobTaskBatchList)) {
                fillParentOperationReason(allJobTaskBatchList, parentJobTaskBatchList, parentWorkflowNode, context);
            }

            workflowExecutor.execute(context);

            evaluationResult = context.getEvaluationResult();
        }

    }

    private boolean arePredecessorsComplete(final WorkflowNodeTaskExecuteDTO taskExecute, Set<BigInteger> predecessors,
                                            Map<BigInteger, List<JobTaskBatch>> jobTaskBatchMap, WorkflowNode waitExecWorkflowNode,
                                            Map<BigInteger, WorkflowNode> workflowNodeMap) {

        // 判断所有节点是否都完成
        for (BigInteger nodeId : predecessors) {
            if (SystemConstants.ROOT.equals(nodeId)) {
                continue;
            }

            List<JobTaskBatch> jobTaskBatches = jobTaskBatchMap.get(nodeId);
            // 说明此节点未执行, 继续等待执行完成
            if (CollectionUtils.isEmpty(jobTaskBatches)) {
                SilenceJobLog.LOCAL.info("批次为空存在未完成的兄弟节点. [{}] 待执行节点:[{}]", nodeId,
                        waitExecWorkflowNode.getId());
                return Boolean.FALSE;
            }

            boolean isCompleted = jobTaskBatches.stream().anyMatch(
                    jobTaskBatch -> JobTaskBatchStatus.NOT_COMPLETE.contains(jobTaskBatch.getTaskBatchStatus()));
            if (isCompleted) {
                SilenceJobLog.LOCAL.info("存在未完成的兄弟节点. [{}] 待执行节点:[{}] parentId:[{}]", nodeId,
                        taskExecute.getParentId(),
                        waitExecWorkflowNode.getId());
                return Boolean.FALSE;
            }

            // 父节点只要有一个是失败的且失败策略是阻塞的则当前节点不处理
            if (jobTaskBatches.stream()
                    .anyMatch(jobTaskBatch ->
                            jobTaskBatch.getTaskBatchStatus() != JobTaskBatchStatus.SUCCESS
                                    && !JobOperationReason.WORKFLOW_SUCCESSOR_SKIP_EXECUTION.contains(jobTaskBatch.getOperationReason()))
            ) {

                WorkflowNode preWorkflowNode = workflowNodeMap.get(nodeId);
                // 根据失败策略判断是否继续处理
                if (Objects.equals(preWorkflowNode.getFailStrategy(), FailStrategy.BLOCK)) {
                    SilenceJobLog.LOCAL.info("此节点执行失败且失败策略配置了【阻塞】中断执行 [{}] 待执行节点:[{}] parentId:[{}]", nodeId,
                            taskExecute.getParentId(),
                            waitExecWorkflowNode.getId());
                    return Boolean.FALSE;
                }
            }
        }

        return Boolean.TRUE;
    }

    private void handlerTaskBatch(WorkflowNodeTaskExecuteDTO taskExecute, JobTaskBatchStatus taskStatus, JobOperationReason operationReason) {

        WorkflowTaskBatch jobTaskBatch = new WorkflowTaskBatch();
        jobTaskBatch.setId(taskExecute.getWorkflowTaskBatchId());
        jobTaskBatch.setExecutionAt(DateUtils.toNowMilli());
        jobTaskBatch.setTaskBatchStatus(taskStatus);
        jobTaskBatch.setOperationReason(operationReason);
        jobTaskBatch.setUpdatedDate(Instant.now());
        Assert.isTrue(1 == workflowTaskBatchDao.updateById(jobTaskBatch),
                () -> new SilenceJobServerException("更新任务失败"));

    }

}
