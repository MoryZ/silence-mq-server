package com.old.silence.job.server.job.task.support.request;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.net.url.UrlQuery;
import cn.hutool.core.util.StrUtil;
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
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.GroupConfigDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobDao;
import com.old.silence.job.server.job.task.dto.JobTaskPrepareDTO;
import com.old.silence.job.server.job.task.support.JobPrepareHandler;
import com.old.silence.job.server.job.task.support.JobTaskConverter;


/**
 * OPENAPI
 * 调度定时任务
 */
@Component
public class OpenApiTriggerJobRequestHandler extends PostHttpRequestHandler {
    private final JobDao jobDao;
    private final GroupConfigDao groupConfigDao;
    private final JobPrepareHandler terminalJobPrepareHandler;

    public OpenApiTriggerJobRequestHandler(JobDao jobDao, GroupConfigDao groupConfigDao,
                                           JobPrepareHandler terminalJobPrepareHandler) {
        this.jobDao = jobDao;
        this.groupConfigDao = groupConfigDao;
        this.terminalJobPrepareHandler = terminalJobPrepareHandler;
    }

    @Override
    public boolean supports(String path) {
        return HTTP_PATH.OPENAPI_TRIGGER_JOB.equals(path);
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
        JobTriggerDTO jobTriggerDTO = JSON.parseObject(JSON.toJSONString(args[0]), JobTriggerDTO.class);
        Job job = jobDao.selectById(jobTriggerDTO.getJobId());
        Assert.notNull(job, () -> new SilenceJobServerException("job can not be null."));

        long count = groupConfigDao.selectCount(new LambdaQueryWrapper<GroupConfig>()
                .eq(GroupConfig::getGroupName, job.getGroupName())
                .eq(GroupConfig::getNamespaceId, job.getNamespaceId())
                .eq(GroupConfig::getGroupStatus, true)
        );

        if (count <= 0) {
            SilenceJobLog.LOCAL.warn("组:[{}]已经关闭，不支持手动执行.", job.getGroupName());
            return new SilenceJobRpcResult(false, retryRequest.getReqId());
        }
        JobTaskPrepareDTO jobTaskPrepare = JobTaskConverter.INSTANCE.toJobTaskPrepare(job);
        // 设置now表示立即执行
        jobTaskPrepare.setNextTriggerAt(DateUtils.toNowMilli());
        jobTaskPrepare.setTaskExecutorScene(JobTaskExecutorScene.MANUAL_JOB);
        // 设置手动参数
        if (StrUtil.isNotBlank(jobTriggerDTO.getTmpArgsStr())) {
            jobTaskPrepare.setTmpArgsStr(jobTriggerDTO.getTmpArgsStr());
        }
        // 创建批次
        terminalJobPrepareHandler.handle(jobTaskPrepare);

        return new SilenceJobRpcResult(true, retryRequest.getReqId());
    }
}
