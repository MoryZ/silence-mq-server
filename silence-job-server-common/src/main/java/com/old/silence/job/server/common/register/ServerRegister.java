package com.old.silence.job.server.common.register;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.enums.NodeType;
import com.old.silence.job.common.util.NetUtils;
import com.old.silence.job.common.util.StreamUtils;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.cache.CacheConsumerGroup;
import com.old.silence.job.server.common.cache.CacheRegisterTable;
import com.old.silence.job.server.common.config.SystemProperties;
import com.old.silence.job.server.common.dto.ServerNodeExtAttrs;
import com.old.silence.job.server.domain.model.ServerNode;
import com.old.silence.job.server.infrastructure.persistence.dao.ServerNodeDao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 服务端注册
 *
 */
@Component(ServerRegister.BEAN_NAME)
public class ServerRegister extends AbstractRegister {
    public static final String BEAN_NAME = "serverRegister";
    private final ScheduledExecutorService serverRegisterNode =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "server-register-node"));
    public static final int DELAY_TIME = 30;
    public static final String CURRENT_CID;
    public static final String GROUP_NAME = "DEFAULT_SERVER";
    public static final String NAMESPACE_ID = "DEFAULT_SERVER_NAMESPACE_ID";

    private final SystemProperties systemProperties;
    private final ServerProperties serverProperties;

    static {
        CURRENT_CID = IdUtil.getSnowflakeNextIdStr();
    }

    protected ServerRegister(ServerNodeDao serverNodeDao, SystemProperties systemProperties,
                             ServerProperties serverProperties) {
        super(serverNodeDao);
        this.systemProperties = systemProperties;
        this.serverProperties = serverProperties;
    }


    @Override
    public boolean supports(NodeType type) {
        return getNodeType().equals(type);
    }

    @Override
    protected void beforeProcessor(RegisterContext context) {
        // 新增扩展参数
        ServerNodeExtAttrs serverNodeExtAttrs = new ServerNodeExtAttrs();
        serverNodeExtAttrs.setWebPort(serverProperties.getPort());

        context.setGroupName(GROUP_NAME);
        context.setHostId(CURRENT_CID);
        context.setHostIp(NetUtils.getLocalIpStr());
        context.setHostPort(systemProperties.getServerPort());
        context.setContextPath(Optional.ofNullable(serverProperties.getServlet().getContextPath()).orElse(StrUtil.EMPTY));
        context.setNamespaceId(NAMESPACE_ID);
        context.setExtAttrs(JSON.toJSONString(serverNodeExtAttrs));
    }

    @Override
    protected Instant getExpireAt() {
        return Instant.now().plusSeconds(DELAY_TIME);
    }

    @Override
    protected boolean doRegister(RegisterContext context, ServerNode serverNode) {
        var objects = new ArrayList<ServerNode>();
        objects.add(serverNode);
        refreshExpireAt(objects);
        return Boolean.TRUE;
    }


    @Override
    protected void afterProcessor(ServerNode serverNode) {
        try {
            // 同步当前POD消费的组的节点信息
            // netty的client只会注册到一个服务端，若组分配的和client连接的不是一个POD则会导致当前POD没有其他客户端的注册信息
            ConcurrentMap<String /*groupName*/, Set<String>/*namespaceId*/> allConsumerGroupName = CacheConsumerGroup.getAllConsumerGroupName();
            if (CollectionUtils.isNotEmpty(allConsumerGroupName)) {
                Set<String> namespaceIdSets = StreamUtils.toSetByFlatMap(allConsumerGroupName.values(), Set::stream);
                if (CollectionUtils.isEmpty(namespaceIdSets)) {
                    return;
                }

                List<ServerNode> serverNodes = serverNodeDao.selectList(
                        new LambdaQueryWrapper<ServerNode>()
                                .eq(ServerNode::getNodeType, NodeType.CLIENT)
                                .in(ServerNode::getNamespaceId, namespaceIdSets)
                                .in(ServerNode::getGroupName, allConsumerGroupName.keySet()));
                for (ServerNode node : serverNodes) {
                    // 刷新全量本地缓存
                    CacheRegisterTable.addOrUpdate(node);
                    // 刷新过期时间
                    CacheConsumerGroup.addOrUpdate(node.getGroupName(), node.getNamespaceId());
                }
            }
        } catch (Exception e) {
            SilenceJobLog.LOCAL.error("刷新客户端失败", e);
        }
    }

    @Override
    protected NodeType getNodeType() {
        return NodeType.SERVER;
    }

    @Override
    public void start() {
        SilenceJobLog.LOCAL.info("ServerRegister start");

        serverRegisterNode.scheduleAtFixedRate(() -> {
            try {
                this.register(new RegisterContext());
            } catch (Exception e) {
                SilenceJobLog.LOCAL.error("服务端注册失败", e);
            }
        }, 0, DELAY_TIME * 2 / 3, TimeUnit.SECONDS);

    }

    @Override
    public void close() {
        SilenceJobLog.LOCAL.info("ServerRegister close");
    }
}
