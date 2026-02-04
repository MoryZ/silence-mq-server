package com.old.silence.job.server.domain.service;

import cn.hutool.core.util.StrUtil;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.old.silence.job.common.enums.NodeType;
import com.old.silence.job.common.model.ApiResult;
import com.old.silence.job.common.util.NetUtils;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.api.assembler.ServerNodeResponseVOMapper;
import com.old.silence.job.server.common.dto.DistributeInstance;
import com.old.silence.job.server.common.dto.ServerNodeExtAttrs;
import com.old.silence.job.server.common.register.ServerRegister;
import com.old.silence.job.server.domain.model.ServerNode;
import com.old.silence.job.server.dto.ServerNodeQuery;
import com.old.silence.job.server.infrastructure.persistence.dao.ServerNodeDao;
import com.old.silence.job.server.vo.ServerNodeResponseVO;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.page.PageImpl;

/**
 * @author moryzang
 */
@Service
public class PodsService {

    private static final String DASHBOARD_CONSUMER_BUCKET = "/dashboard/consumer/bucket";
    private final ServerNodeDao serverNodeDao;
    private final RestTemplate restTemplate;
    private final ServerProperties serverProperties;
    private final ServerNodeResponseVOMapper serverNodeResponseVOMapper;

    public PodsService(ServerNodeDao serverNodeDao, RestTemplate restTemplate,
                       ServerProperties serverProperties, ServerNodeResponseVOMapper serverNodeResponseVOMapper) {
        this.serverNodeDao = serverNodeDao;
        this.restTemplate = restTemplate;
        this.serverProperties = serverProperties;
        this.serverNodeResponseVOMapper = serverNodeResponseVOMapper;
    }

    public IPage<ServerNodeResponseVO> pods(Page<ServerNode> pageDTO, ServerNodeQuery queryVO) {

        // TODO 查询所有的？
        LambdaQueryWrapper<ServerNode> serverNodeLambdaQueryWrapper = new LambdaQueryWrapper<ServerNode>()
                .eq(StrUtil.isNotBlank(queryVO.getGroupName()), ServerNode::getGroupName, queryVO.getGroupName())
                .ge(ServerNode::getExpireAt, Instant.now().minusSeconds(ServerRegister.DELAY_TIME + (ServerRegister.DELAY_TIME / 3)))
                .orderByDesc(ServerNode::getNodeType);
        Page<ServerNode> serverNodePageDTO = serverNodeDao.selectPage(pageDTO, serverNodeLambdaQueryWrapper);
        List<ServerNodeResponseVO> responseVOList = CollectionUtils.transformToList(serverNodePageDTO.getRecords(),
                serverNodeResponseVOMapper::convert);

        for (ServerNodeResponseVO serverNodeResponseVO : responseVOList) {
            if (NodeType.CLIENT.equals(serverNodeResponseVO.getNodeType())) {
                continue;
            }

            // 若是本地节点则直接从缓存中取
            if (ServerRegister.CURRENT_CID.equals(serverNodeResponseVO.getHostId())) {
                serverNodeResponseVO.setConsumerBuckets(DistributeInstance.INSTANCE.getConsumerBucket());
                continue;
            }
            if (StringUtils.isBlank(serverNodeResponseVO.getExtAttrs())) {
                continue;
            }
            ServerNodeExtAttrs serverNodeExtAttrs = JSON.parseObject(serverNodeResponseVO.getExtAttrs(), ServerNodeExtAttrs.class);
            try {
                // 从远程节点取
                String url = NetUtils.getUrl(serverNodeResponseVO.getHostIp(), serverNodeExtAttrs.getWebPort(), serverProperties.getServlet().getContextPath());
                ApiResult<List<Integer>> result = restTemplate.getForObject(url.concat(DASHBOARD_CONSUMER_BUCKET), ApiResult.class);
                List<Integer> data = result.getData();
                if (CollectionUtils.isNotEmpty(data)) {
                    serverNodeResponseVO.setConsumerBuckets(data.stream()
                            .sorted(Integer::compareTo)
                            .collect(Collectors.toCollection(LinkedHashSet::new)));
                }
            } catch (Exception e) {
                SilenceJobLog.LOCAL.error("Failed to retrieve consumer group for node [{}:{}].", serverNodeResponseVO.getHostIp(), serverNodeExtAttrs.getWebPort());
            }
        }
        return new PageImpl<>(responseVOList, serverNodePageDTO.getTotal());
    }

}
