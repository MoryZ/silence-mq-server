package com.old.silence.job.server.job.task.client;


import com.old.silence.job.common.client.dto.StopJobDTO;
import com.old.silence.job.common.client.dto.request.DispatchJobRequest;
import com.old.silence.job.common.model.ApiResult;


import static com.old.silence.job.common.constant.SystemConstants.HTTP_PATH.JOB_DISPATCH;
import static com.old.silence.job.common.constant.SystemConstants.HTTP_PATH.JOB_STOP;
import com.old.silence.job.server.common.rpc.client.RequestMethod;
import com.old.silence.job.server.common.rpc.client.annotation.Body;
import com.old.silence.job.server.common.rpc.client.annotation.Mapping;


public interface JobRpcClient {

    @Mapping(path = JOB_STOP, method = RequestMethod.POST)
    ApiResult<Boolean> stop(@Body StopJobDTO stopJobDTO);

    @Mapping(path = JOB_DISPATCH, method = RequestMethod.POST)
    ApiResult<Boolean> dispatch(@Body DispatchJobRequest dispatchJobRequest);

}
