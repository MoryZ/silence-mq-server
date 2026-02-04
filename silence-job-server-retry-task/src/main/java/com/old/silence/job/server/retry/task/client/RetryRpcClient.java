package com.old.silence.job.server.retry.task.client;


import com.old.silence.job.common.client.dto.GenerateRetryIdempotentIdDTO;
import com.old.silence.job.common.client.dto.request.DispatchRetryRequest;
import com.old.silence.job.common.client.dto.request.RetryCallbackRequest;
import com.old.silence.job.common.client.dto.request.StopRetryRequest;
import com.old.silence.job.common.model.ApiResult;
import com.old.silence.job.common.model.SilenceJobHeaders;

import static com.old.silence.job.common.constant.SystemConstants.HTTP_PATH.RETRY_CALLBACK;
import static com.old.silence.job.common.constant.SystemConstants.HTTP_PATH.RETRY_DISPATCH;
import static com.old.silence.job.common.constant.SystemConstants.HTTP_PATH.RETRY_GENERATE_IDEM_ID;
import static com.old.silence.job.common.constant.SystemConstants.HTTP_PATH.RETRY_STOP;
import com.old.silence.job.server.common.rpc.client.RequestMethod;
import com.old.silence.job.server.common.rpc.client.annotation.Body;
import com.old.silence.job.server.common.rpc.client.annotation.Header;
import com.old.silence.job.server.common.rpc.client.annotation.Mapping;

/**
 * 调用客户端接口
 *
 */
public interface RetryRpcClient {

    @Mapping(path = RETRY_DISPATCH, method = RequestMethod.POST)
    ApiResult<Boolean> dispatch(@Body DispatchRetryRequest dispatchRetryRequest, @Header SilenceJobHeaders headers);

    @Mapping(path = RETRY_STOP, method = RequestMethod.POST)
    ApiResult<Boolean> stop(@Body StopRetryRequest stopRetryRequest);

    @Mapping(path = RETRY_CALLBACK, method = RequestMethod.POST)
    ApiResult<Boolean> callback(@Body RetryCallbackRequest retryCallbackRequest);

    @Mapping(path = RETRY_GENERATE_IDEM_ID, method = RequestMethod.POST)
    ApiResult<Void> generateIdempotentId(@Body GenerateRetryIdempotentIdDTO retryCallbackDTO);

}
