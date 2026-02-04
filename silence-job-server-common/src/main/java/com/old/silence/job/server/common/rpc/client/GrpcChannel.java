package com.old.silence.job.server.common.rpc.client;

import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.common.context.SilenceSpringContext;
import com.old.silence.job.common.enums.HeadersEnum;
import com.old.silence.job.common.grpc.auto.GrpcResult;
import com.old.silence.job.common.grpc.auto.GrpcSilenceJobRequest;
import com.old.silence.job.common.grpc.auto.Metadata;
import com.old.silence.job.common.util.NetUtils;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.config.SystemProperties;
import com.old.silence.job.server.common.config.SystemProperties.RpcClientProperties;
import com.old.silence.job.server.common.config.SystemProperties.ThreadPoolConfig;
import com.old.silence.job.server.common.register.ServerRegister;
import com.old.silence.job.server.common.triple.Pair;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;



public class GrpcChannel {
    private GrpcChannel() {
    }
    private static final ThreadPoolExecutor grpcExecutor = createGrpcExecutor();
    private static final ConcurrentHashMap<Pair<String, String>, ManagedChannel> CHANNEL_MAP = new ConcurrentHashMap<>(16);

    public static void setChannel(String hostId, String ip, ManagedChannel channel) {
        CHANNEL_MAP.put(Pair.of(hostId, ip), channel);
    }

    public static void removeChannel(ManagedChannel channel) {
        CHANNEL_MAP.forEach((key, value) -> {
            if (value.equals(channel)) {
                CHANNEL_MAP.remove(key);
            }
        });
    }


    /**
     * 发送数据
     *
     * @param url   url地址
     * @param body  请求的消息体
     * @param reqId 请求id
     * @throws InterruptedException 终端异常
     */
    public static ListenableFuture<GrpcResult> send(String hostId, String hostIp, Integer port, String url, String body, Map<String, String> headersMap,
        final long reqId) {

        ManagedChannel channel = CHANNEL_MAP.get(Pair.of(hostId, hostIp));
        if (Objects.isNull(channel) || channel.isShutdown() || channel.isTerminated()) {
            removeChannel(channel);
            channel = connect(hostId, hostIp, port);
            if (Objects.isNull(channel)) {
                SilenceJobLog.LOCAL.error("send message but channel is null url:[{}] method:[{}] body:[{}] ", url, body);
                return null;
            }
        }
        headersMap.put(HeadersEnum.HOST_ID.getKey(), ServerRegister.CURRENT_CID);
        headersMap.put(HeadersEnum.HOST_IP.getKey(), NetUtils.getLocalIpStr());
        headersMap.put(HeadersEnum.GROUP_NAME.getKey(), ServerRegister.GROUP_NAME);
        headersMap.put(HeadersEnum.HOST_PORT.getKey(), getServerPort());
        headersMap.put(HeadersEnum.NAMESPACE.getKey(), SystemConstants.DEFAULT_NAMESPACE);
        headersMap.put(HeadersEnum.TOKEN.getKey(), getServerToken());

        Metadata metadata = Metadata
            .newBuilder()
            .setUri(url)
            .putAllHeaders(headersMap)
            .build();
        GrpcSilenceJobRequest grpcSilenceJobRequest = GrpcSilenceJobRequest
            .newBuilder()
            .setMetadata(metadata)
            .setReqId(reqId)
            .setBody(body)
            .build();

        MethodDescriptor<GrpcSilenceJobRequest, GrpcResult> methodDescriptor =
            MethodDescriptor.<GrpcSilenceJobRequest, GrpcResult>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("UnaryRequest", "unaryRequest"))
                .setRequestMarshaller(ProtoUtils.marshaller(GrpcSilenceJobRequest.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(GrpcResult.getDefaultInstance()))
                .build();

        // 创建动态代理调用方法
        return io.grpc.stub.ClientCalls.futureUnaryCall(
            channel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT),
            grpcSilenceJobRequest);

    }

    private static String getServerToken() {
        SystemProperties properties = SilenceSpringContext.getBean(SystemProperties.class);
        return properties.getServerToken();
    }

    private static String getServerPort() {
        SystemProperties properties = SilenceSpringContext.getBean(SystemProperties.class);
        return String.valueOf(properties.getServerPort());
    }

    /**
     * 连接客户端
     *
     */
    public static ManagedChannel connect(String hostId, String ip, Integer port) {

        try {
            RpcClientProperties clientRpc = SilenceSpringContext.getBean(SystemProperties.class).getClientRpc();
            ManagedChannel channel = ManagedChannelBuilder.forAddress(ip, port)
                .executor(grpcExecutor)
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .maxInboundMessageSize(clientRpc.getMaxInboundMessageSize())
                .keepAliveTime(clientRpc.getKeepAliveTime().toMillis(), TimeUnit.MILLISECONDS)
                .keepAliveTimeout(clientRpc.getKeepAliveTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .usePlaintext()
                .build();
            GrpcChannel.setChannel(hostId, ip, channel);

            return channel;
        } catch (Exception e) {
            exceptionHandler(e);
        }

        return null;
    }

    private static ThreadPoolExecutor createGrpcExecutor() {
        RpcClientProperties clientRpc = SilenceSpringContext.getBean(SystemProperties.class).getClientRpc();
        ThreadPoolConfig clientTp = clientRpc.getClientTp();
        ThreadPoolExecutor grpcExecutor = new ThreadPoolExecutor(clientTp.getCorePoolSize(),
            clientTp.getMaximumPoolSize(), clientTp.getKeepAliveTime(), TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(clientTp.getQueueCapacity()),
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("silence-job-grpc-client-executor-%d")
                .build());
        grpcExecutor.allowCoreThreadTimeOut(true);
        return grpcExecutor;
    }

    /**
     * 连接失败处理
     *
     * @param cause
     */
    private static void exceptionHandler(Throwable cause) {
        if (cause instanceof ConnectException) {
            SilenceJobLog.LOCAL.error("connect error:{}", cause.getMessage());
        } else if (cause instanceof ClosedChannelException) {
            SilenceJobLog.LOCAL.error("connect error:{}", "client has destroy");
        } else {
            SilenceJobLog.LOCAL.error("connect error:", cause);
        }
    }

}
