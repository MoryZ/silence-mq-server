package com.old.silence.job.server.job.task.support.request;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.net.url.UrlQuery;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.old.silence.job.common.constant.SystemConstants.HTTP_PATH;
import com.old.silence.job.common.enums.JobTaskExecutorScene;
import com.old.silence.job.common.model.SilenceJobRequest;
import com.old.silence.job.common.model.SilenceJobRpcResult;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.dto.JobTriggerDTO;
import com.old.silence.job.server.common.handler.PostHttpRequestHandler;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.domain.model.Workflow;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.GroupConfigDao;
import com.old.silence.job.server.infrastructure.persistence.dao.WorkflowDao;
import com.old.silence.job.server.job.task.dto.WorkflowTaskPrepareDTO;
import com.old.silence.job.server.job.task.support.WorkflowPrePareHandler;
import com.old.silence.job.server.job.task.support.WorkflowTaskConverter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * OPENAPI
 * 新增工作流任务
 */
@Component
public class OpenApiTriggerWorkFlowRequestHandler extends PostHttpRequestHandler {
    private final WorkflowDao workflowDao;
    private final GroupConfigDao groupConfigDao;
    private final WorkflowPrePareHandler terminalWorkflowPrepareHandler;

    public OpenApiTriggerWorkFlowRequestHandler(WorkflowDao workflowDao, GroupConfigDao groupConfigDao,
                                                WorkflowPrePareHandler terminalWorkflowPrepareHandler) {
        this.workflowDao = workflowDao;
        this.groupConfigDao = groupConfigDao;
        this.terminalWorkflowPrepareHandler = terminalWorkflowPrepareHandler;
    }

    @Override
    public boolean supports(String path) {
        return HTTP_PATH.OPENAPI_TRIGGER_WORKFLOW.equals(path);
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.POST;
    }

    @Override
    public SilenceJobRpcResult doHandler(String content, UrlQuery query, HttpHeaders headers) {
        SilenceJobLog.LOCAL.debug("Trigger job content:[{}]", content);
        SilenceJobRequest retryRequest = JSON.parseObject(content, SilenceJobRequest.class);
        Object[] args = retryRequest.getArgs();
        JobTriggerDTO workflowDTO = JSON.parseObject(JSON.toJSONString(args[0]), JobTriggerDTO.class);
        Workflow workflow = workflowDao.selectById(workflowDTO.getJobId());
        Assert.notNull(workflow, () -> new SilenceJobServerException("workflow can not be null."));

        // 将字符串反序列化为 Set
        if (StrUtil.isNotBlank(workflow.getGroupName())) {
            Set<String> namesSet = new HashSet<>(Arrays.asList(workflow.getGroupName().split(", ")));

            // 判断任务节点相关组有无关闭，存在关闭组则停止执行工作流执行
            if (CollectionUtil.isNotEmpty(namesSet)) {
                for (String groupName : namesSet) {
                    long count = groupConfigDao.selectCount(
                            new LambdaQueryWrapper<GroupConfig>()
                                    .eq(GroupConfig::getGroupName, groupName)
                                    .eq(GroupConfig::getNamespaceId, workflow.getNamespaceId())
                                    .eq(GroupConfig::getGroupStatus, true)
                    );

                    if (count <= 0) {
                        SilenceJobLog.LOCAL.warn("组:[{}]已经关闭，不支持手动执行.", workflow.getGroupName());
                        return new SilenceJobRpcResult(false, retryRequest.getReqId());
                    }
                }
            }
        }

        WorkflowTaskPrepareDTO prepareDTO = WorkflowTaskConverter.INSTANCE.toWorkflowTaskPrepareDTO(workflow);
        // 设置now表示立即执行
        prepareDTO.setNextTriggerAt(DateUtils.toNowMilli());
        prepareDTO.setTaskExecutorScene(JobTaskExecutorScene.MANUAL_WORKFLOW);
//        设置工作流上下文
        String tmpWfContext = workflowDTO.getTmpArgsStr();
        if (StrUtil.isNotBlank(tmpWfContext) && !JSON.isValid(tmpWfContext)) {
            prepareDTO.setWfContext(tmpWfContext);
        }
        terminalWorkflowPrepareHandler.handler(prepareDTO);

        return new SilenceJobRpcResult(true, retryRequest.getReqId());

    }
}
