package com.old.silence.job.server.common.rpc.client;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.lang.Assert;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.fastjson2.JSON;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.util.concurrent.ListenableFuture;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.common.context.SilenceSpringContext;
import com.old.silence.job.common.exception.SilenceJobRemotingTimeOutException;
import com.old.silence.job.common.grpc.auto.GrpcResult;
import com.old.silence.job.common.model.ApiResult;
import com.old.silence.job.common.util.NetUtils;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.cache.CacheRegisterTable;
import com.old.silence.job.server.common.cache.CacheToken;
import com.old.silence.job.server.common.dto.RegisterNodeInfo;
import com.old.silence.job.server.common.handler.ClientNodeAllocateHandler;
import com.old.silence.job.server.common.rpc.client.annotation.Body;
import com.old.silence.job.server.common.rpc.client.annotation.Header;
import com.old.silence.job.server.common.rpc.client.annotation.Mapping;
import com.old.silence.job.server.common.rpc.client.annotation.Param;
import com.old.silence.job.server.exception.SilenceJobServerException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求处理器
 *
 */
public class GrpcClientInvokeHandler implements InvocationHandler {

    public static final AtomicLong REQUEST_ID = new AtomicLong(0);
    private static final Logger log = LoggerFactory.getLogger(GrpcClientInvokeHandler.class);


    private final String groupName;
    private String hostId;
    private String hostIp;
    private Integer hostPort;
    private final boolean failRetry;
    private final int retryTimes;
    private final int retryInterval;
    private final RetryListener retryListener;
    private final boolean failover;
    private final Integer routeKey;
    private final String allocKey;
    private final Integer executorTimeout;
    private final String namespaceId;
    private final boolean async;

    public GrpcClientInvokeHandler(String groupName, RegisterNodeInfo registerNodeInfo, boolean failRetry,
                                   int retryTimes, int retryInterval, RetryListener retryListener, Integer routeKey,
                                   String allocKey, boolean failover, Integer executorTimeout, String namespaceId) {
        this.groupName = groupName;
        this.hostId = registerNodeInfo.getHostId();
        this.hostPort = registerNodeInfo.getHostPort();
        this.hostIp = registerNodeInfo.getHostIp();
        this.failRetry = failRetry;
        this.retryTimes = retryTimes;
        this.retryInterval = retryInterval;
        this.retryListener = retryListener;
        this.failover = failover;
        this.routeKey = routeKey;
        this.allocKey = allocKey;
        this.executorTimeout = executorTimeout;
        this.namespaceId = namespaceId;
        this.async = false;
    }

    @Override
    public ApiResult invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Mapping annotation = method.getAnnotation(Mapping.class);
        Assert.notNull(annotation, () -> new SilenceJobServerException("@Mapping cannot be null"));

        if (failover) {
            return doFailoverHandler(method, args, annotation);
        }

        return requestRemote(method, args, annotation, 1);
    }

    @NotNull
    private ApiResult doFailoverHandler(Method method, Object[] args, Mapping annotation)
        throws Throwable {
        Set<RegisterNodeInfo> serverNodeSet = CacheRegisterTable.getServerNodeSet(groupName, namespaceId);

        // 最多调用size次
        int size = serverNodeSet.size();
        for (int count = 1; count <= size; count++) {
            log.debug("Start request client. count:[{}] clientId:[{}] clientAddress:[{}:{}] serverIp:[{}]", count, hostId,
                hostIp, hostPort, NetUtils.getLocalIpStr());
            if (retryListener instanceof SilenceJobRetryListener) {
                // 传递修改之后的客户端节点信息
                SilenceJobRetryListener listener = (SilenceJobRetryListener) retryListener;
                Map<String, Object> properties = listener.properties();
                properties.put("HOST_ID", hostId);
                properties.put("HOST_IP", hostIp);
                properties.put("HOST_PORT", hostPort);
            }

            ApiResult result = requestRemote(method, args, annotation, count);
            if (Objects.nonNull(result)) {
                return result;
            }
        }

        throw new SilenceJobServerException("No available nodes.");
    }

    private ApiResult requestRemote(Method method, Object[] args, Mapping mapping, int count) throws Throwable {

        try {

            // 参数解析
            ParseParasResult parasResult = doParseParams(method, args);

            // 若是POST请求，请求体不能是null
            if (RequestMethod.POST.name().equals(mapping.method().name())) {
                Assert.notNull(parasResult.body, () -> new SilenceJobServerException("body cannot be null"));
            }

            Retryer<ApiResult> retryer = buildResultRetryer();

            Map<String, String> requestHeaders = parasResult.requestHeaders;
            // 统一设置Token
            requestHeaders.put(SystemConstants.SILENCE_JOB_AUTH_TOKEN, CacheToken.get(groupName, namespaceId));

            long reqId = newId();
            ApiResult result = retryer.call(() -> {

                StopWatch sw = new StopWatch();

                sw.start("request start " + reqId);

                ListenableFuture<GrpcResult> future;
                try {
                    future = GrpcChannel.send(hostId, hostIp, hostPort,
                        mapping.path(), JSON.toJSONString(args), requestHeaders, reqId);
                } finally {
                    sw.stop();
                }

                SilenceJobLog.LOCAL.debug("request complete requestId:[{}] 耗时:[{}ms]", reqId);

                if (async) {
                    // 暂时不支持异步调用
                    return null;
                } else {
                    Assert.notNull(future, () -> new SilenceJobServerException("completableFuture is null"));
                    GrpcResult grpcResult = future.get(Optional.ofNullable(executorTimeout).orElse(20),
                        TimeUnit.SECONDS);
                    Object obj = JSON.parseObject(grpcResult.getData(), Object.class);
                    return new ApiResult<>(grpcResult.getStatus(), grpcResult.getMessage(), obj);
                }

            });

            log.debug("Request client success. count:[{}] clientId:[{}] clientAddr:[{}:{}] serverIp:[{}]", count,
                hostId,
                hostIp, hostPort, NetUtils.getLocalIpStr());

            return result;
        } catch (ExecutionException ex) {
            // 网络异常 TimeoutException |
            if (ex.getCause() instanceof SilenceJobRemotingTimeOutException && failover) {
                log.error("request client I/O error, count:[{}] clientId:[{}] clientAddr:[{}:{}] serverIp:[{}]", count,
                    hostId, hostIp, hostPort, NetUtils.getLocalIpStr(), ex);

                // 进行路由剔除处理
                CacheRegisterTable.remove(groupName, hostId);
                // 重新选一个可用的客户端节点
                ClientNodeAllocateHandler clientNodeAllocateHandler = SilenceSpringContext.getBean(
                    ClientNodeAllocateHandler.class);
                RegisterNodeInfo serverNode = clientNodeAllocateHandler.getServerNode(allocKey, groupName, namespaceId,
                    routeKey);
                // 这里表示无可用节点
                if (Objects.isNull(serverNode)) {
                    throw ex.getCause();
                }

                this.hostId = serverNode.getHostId();
                this.hostPort = serverNode.getHostPort();
                this.hostIp = serverNode.getHostIp();

            } else {
                // 其他异常继续抛出
                log.error("request client error.count:[{}] clientId:[{}] clientAddr:[{}:{}] serverIp:[{}]", count,
                    hostId, hostIp, hostPort, NetUtils.getLocalIpStr(), ex);
                throw ex.getCause();
            }
        } catch (Exception ex) {
            log.error("request client unknown exception. count:[{}] clientId:[{}] clientAddr:[{}:{}] serverIp:[{}]",
                count, hostId, hostIp, hostPort, NetUtils.getLocalIpStr(), ex);

            Throwable throwable = ex;
            if (ex.getClass().isAssignableFrom(RetryException.class)) {
                RetryException re = (RetryException) ex;
                throwable = re.getLastFailedAttempt().getExceptionCause();
                if (throwable.getCause() instanceof SilenceJobRemotingTimeOutException) {
                    // 若重试之后该接口仍然有问题，进行路由剔除处理
                    CacheRegisterTable.remove(groupName, hostId);
                }
            }

            throw throwable;
        }

        return null;
    }

    private Retryer<ApiResult> buildResultRetryer() {
        Retryer<ApiResult> retryer = RetryerBuilder.<ApiResult>newBuilder()
            .retryIfException(throwable -> failRetry)
            .withStopStrategy(StopStrategies.stopAfterAttempt(retryTimes <= 0 ? 1 : retryTimes))
            .withWaitStrategy(WaitStrategies.fixedWait(Math.max(retryInterval, 0), TimeUnit.SECONDS))
            .withRetryListener(retryListener)
            .build();
        return retryer;
    }

    private ParseParasResult doParseParams(Method method, Object[] args) {

        Object body = null;
        Map<String, String> requestHeaders = new HashMap<>();
        Map<String, Object> paramMap = new HashMap<>();
        // 解析参数
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.isAnnotationPresent(Body.class)) {
                body = args[i];
            } else if ((parameter.isAnnotationPresent(Header.class))) {
                requestHeaders.put(SystemConstants.SILENCE_JOB_HEAD_KEY, JSON.toJSONString(args[i]));
            } else if ((parameter.isAnnotationPresent(Param.class))) {
                paramMap.put(parameter.getAnnotation(Param.class).name(), args[i]);
            } else {
                throw new SilenceJobServerException("parameter error");
            }
        }

        ParseParasResult parseParasResult = new ParseParasResult();
        parseParasResult.setBody(body);
        parseParasResult.setParamMap(paramMap);
        parseParasResult.setRequestHeaders(requestHeaders);
        return parseParasResult;
    }

    
    private static class ParseParasResult {

        private Object body = null;
        private Map<String, String> requestHeaders;
        private Map<String, Object> paramMap;

        public Object getBody() {
            return body;
        }

        public void setBody(Object body) {
            this.body = body;
        }

        public Map<String, String> getRequestHeaders() {
            return requestHeaders;
        }

        public void setRequestHeaders(Map<String, String> requestHeaders) {
            this.requestHeaders = requestHeaders;
        }

        public Map<String, Object> getParamMap() {
            return paramMap;
        }

        public void setParamMap(Map<String, Object> paramMap) {
            this.paramMap = paramMap;
        }
    }

    private static long newId() {
        return REQUEST_ID.getAndIncrement();
    }
}
