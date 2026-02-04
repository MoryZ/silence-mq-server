package com.old.silence.job.server.job.task.support.generator.task;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.old.silence.job.common.enums.JobArgsType;
import com.old.silence.job.common.enums.JobTaskStatus;
import com.old.silence.job.common.enums.JobTaskType;
import com.old.silence.job.common.model.JobArgsHolder;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.dto.RegisterNodeInfo;
import com.old.silence.job.server.common.handler.ClientNodeAllocateHandler;
import com.old.silence.job.server.common.util.ClientInfoUtils;
import com.old.silence.job.server.domain.model.JobTask;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.JobTaskDao;
import com.old.silence.job.server.job.task.support.JobTaskConverter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;


@Component
public class ClusterTaskGenerator extends AbstractJobTaskGenerator {
    private static final String TASK_NAME = "CLUSTER_TASK";
    private final ClientNodeAllocateHandler clientNodeAllocateHandler;

    protected ClusterTaskGenerator(JobTaskDao jobTaskDao,
                                   ClientNodeAllocateHandler clientNodeAllocateHandler) {
        super(jobTaskDao);
        this.clientNodeAllocateHandler = clientNodeAllocateHandler;
    }

    @Override
    public JobTaskType getTaskInstanceType() {
        return JobTaskType.CLUSTER;
    }

    @Override
    public List<JobTask> doGenerate(JobTaskGenerateContext context) {
        // 生成可执行任务
        RegisterNodeInfo serverNode = clientNodeAllocateHandler.getServerNode(context.getJobId().toString(),
                context.getGroupName(), context.getNamespaceId(), context.getRouteKey());
        if (Objects.isNull(serverNode)) {
            SilenceJobLog.LOCAL.error("无可执行的客户端信息. jobId:[{}]", context.getJobId());
            return Lists.newArrayList();
        }

        // 新增任务实例
        JobTask jobTask = JobTaskConverter.INSTANCE.toJobTaskInstance(context);
        jobTask.setClientInfo(ClientInfoUtils.generate(serverNode));
        jobTask.setArgsType(JobArgsType.JSON);
        JobArgsHolder jobArgsHolder = new JobArgsHolder();
        jobArgsHolder.setJobParams(context.getArgsStr());
        jobTask.setArgsStr(JSON.toJSONString(jobArgsHolder));
        jobTask.setTaskStatus(JobTaskStatus.RUNNING);
        jobTask.setTaskName(TASK_NAME);
        jobTask.setResultMessage(Optional.ofNullable(jobTask.getResultMessage()).orElse(StrUtil.EMPTY));
        Assert.isTrue(1 == jobTaskDao.insert(jobTask), () -> new SilenceJobServerException("新增任务实例失败"));

        return Lists.newArrayList(jobTask);
    }

}
