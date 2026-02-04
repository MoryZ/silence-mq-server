package com.old.silence.job.server.common.rpc.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.*;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.common.context.SilenceSpringContext;
import com.old.silence.job.common.enums.HeadersEnum;
import com.old.silence.job.common.util.NetUtils;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.config.SystemProperties;
import com.old.silence.job.server.common.register.ServerRegister;
import com.old.silence.job.server.common.triple.Pair;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


public class NettyChannel {
    private static Bootstrap bootstrap;
    private static ConcurrentHashMap<Pair<String, String>, Channel> CHANNEL_MAP = new ConcurrentHashMap<>(16);
    private NettyChannel() {
    }

    public static void setChannel(String hostId, String ip, Channel channel) {
        CHANNEL_MAP.put(Pair.of(hostId, ip), channel);
    }

    public static void removeChannel(Channel channel) {
        CHANNEL_MAP.forEach((key, value) -> {
            if (value.equals(channel)) {
                CHANNEL_MAP.remove(key);
            }
        });
    }

    public static void setBootstrap(Bootstrap bootstrap) {
        NettyChannel.bootstrap = bootstrap;
    }

    /**
     * 发送数据
     *
     * @param method 请求方式
     * @param url    url地址
     * @param body   请求的消息体
     * @throws InterruptedException
     */
    public static synchronized void send(String hostId, String hostIp, Integer port, HttpMethod method, String url, String body, HttpHeaders requestHeaders) throws InterruptedException {

        Channel channel = CHANNEL_MAP.get(Pair.of(hostId, hostIp));
        if (Objects.isNull(channel) || !channel.isActive()) {
            channel = connect(hostId, hostIp, port);
            if (Objects.isNull(channel)) {
                SilenceJobLog.LOCAL.error("send message but channel is null url:[{}] method:[{}] body:[{}] ", url, method, body);
                return;
            }
        }

        // 配置HttpRequest的请求数据和一些配置信息
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, method, url, Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)));

        request.headers()
                // Host
                .set(HttpHeaderNames.HOST, hostIp)
                // Content-Type
                .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                // 开启长连接
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                // 设置传递请求内容的长度
                .set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes())
                .set(HeadersEnum.HOST_ID.getKey(), ServerRegister.CURRENT_CID)
                .set(HeadersEnum.HOST_IP.getKey(), NetUtils.getLocalIpStr())
                .set(HeadersEnum.GROUP_NAME.getKey(), ServerRegister.GROUP_NAME)
                .set(HeadersEnum.HOST_PORT.getKey(), getServerPort())
                .set(HeadersEnum.NAMESPACE.getKey(), SystemConstants.DEFAULT_NAMESPACE)
                .set(HeadersEnum.TOKEN.getKey(), getServerToken())
        ;
        request.headers().setAll(requestHeaders);

        //发送数据
        channel.writeAndFlush(request).sync();
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
     * @return
     */
    public static Channel connect(String hostId, String ip, Integer port) {

        try {
            ChannelFuture channelFuture = bootstrap
                    .remoteAddress(ip, port)
                    .connect();

            boolean notTimeout = channelFuture.awaitUninterruptibly(30, TimeUnit.SECONDS);
            Channel channel = channelFuture.channel();
            if (notTimeout) {
                // 连接成功
                if (channel != null && channel.isActive()) {
                    SilenceJobLog.LOCAL.info("netty client started {} connect to server id:[{}] ip:[{}] channel:[{}]",
                            channel.localAddress(), hostId, ip, channel);
                    NettyChannel.setChannel(hostId, ip, channel);
                    return channel;
                }

                Throwable cause = channelFuture.cause();
                if (cause != null) {
                    exceptionHandler(cause);
                }
            } else {
                SilenceJobLog.LOCAL.warn("connect remote host[{}] timeout {}s", channel.remoteAddress(), 30);
            }
        } catch (Exception e) {
            exceptionHandler(e);
        }

        return null;
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
