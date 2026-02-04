package com.old.silence.job.server.retry.task.support.dispatch;

import org.apache.pekko.actor.AbstractActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.old.silence.job.common.client.dto.request.StopRetryRequest;
import com.old.silence.job.common.model.ApiResult;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.cache.CacheRegisterTable;
import com.old.silence.job.server.common.dto.RegisterNodeInfo;
import com.old.silence.job.server.common.pekko.ActorGenerator;
import com.old.silence.job.server.common.rpc.client.RequestBuilder;
import com.old.silence.job.server.retry.task.client.RetryRpcClient;
import com.old.silence.job.server.retry.task.dto.RequestStopRetryTaskExecutorDTO;
import com.old.silence.job.server.retry.task.support.RetryTaskConverter;

import java.util.Objects;


@Component(ActorGenerator.RETRY_REAL_STOP_TASK_INSTANCE_ACTOR)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)

public class RequestStopClientActor extends AbstractActor {


    private static final Logger log = LoggerFactory.getLogger(RequestStopClientActor.class);

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(RequestStopRetryTaskExecutorDTO.class, taskExecutorDTO -> {
            try {
                doStop(taskExecutorDTO);
            } catch (Exception e) {
                log.error("请求客户端发生异常", e);
            }
        }).build();
    }

    private void doStop(RequestStopRetryTaskExecutorDTO executorDTO) {
        // 检查客户端是否存在
        RegisterNodeInfo registerNodeInfo = CacheRegisterTable.getServerNode(
                executorDTO.getGroupName(),
                executorDTO.getNamespaceId(),
                executorDTO.getClientId());
        if (Objects.isNull(registerNodeInfo)) {
            return;
        }

        // 不用关心停止的结果，若服务端尝试终止失败,客户端会兜底进行关闭
        StopRetryRequest stopRetryRequest = RetryTaskConverter.INSTANCE.toStopRetryRequest(executorDTO);

        try {
            // 构建请求客户端对象
            RetryRpcClient rpcClient = buildRpcClient(registerNodeInfo);
            ApiResult<Boolean> dispatch = rpcClient.stop(stopRetryRequest);
            if (dispatch.getCode() == 200) {
                SilenceJobLog.LOCAL.info("retryTaskId:[{}] 任务停止成功.", executorDTO.getRetryTaskId());
            } else {
                // 客户端返回失败，则认为任务执行失败
                SilenceJobLog.LOCAL.warn("retryTaskId:[{}] 任务停止失败.", executorDTO.getRetryTaskId());
            }

        } catch (Exception e) {
            SilenceJobLog.LOCAL.error("retryTaskId:[{}] 任务停止失败.", executorDTO.getRetryTaskId(), e);
        }

    }

    private RetryRpcClient buildRpcClient(RegisterNodeInfo registerNodeInfo) {
        return RequestBuilder.<RetryRpcClient, ApiResult>newBuilder()
                .nodeInfo(registerNodeInfo)
                .failRetry(true)
                .retryTimes(3)
                .retryInterval(1)
                .client(RetryRpcClient.class)
                .build();
    }
}