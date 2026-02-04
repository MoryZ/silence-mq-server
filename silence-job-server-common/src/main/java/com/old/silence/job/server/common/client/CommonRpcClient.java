package com.old.silence.job.server.common.client;

 
import com.old.silence.job.common.model.ApiResult;
import com.old.silence.job.common.dto.ConfigDTO;
import com.old.silence.job.server.common.dto.PullRemoteNodeClientRegisterInfoDTO;

import static com.old.silence.job.common.constant.SystemConstants.HTTP_PATH.GET_REG_NODES_AND_REFRESH;
import static com.old.silence.job.common.constant.SystemConstants.HTTP_PATH.SYNC_CONFIG;
import com.old.silence.job.server.common.rpc.client.RequestMethod;
import com.old.silence.job.server.common.rpc.client.annotation.Body;
import com.old.silence.job.server.common.rpc.client.annotation.Mapping;

/**
 * 调用客户端接口
 *
 */
public interface CommonRpcClient {

    @Mapping(path = SYNC_CONFIG, method = RequestMethod.POST)
    ApiResult syncConfig(@Body ConfigDTO configDTO);

    @Mapping(path = GET_REG_NODES_AND_REFRESH, method = RequestMethod.POST)
    ApiResult<String> pullRemoteNodeClientRegisterInfo(@Body PullRemoteNodeClientRegisterInfoDTO registerInfo);
}
