package com.old.silence.job.server.common.alarm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEvent;
import com.alibaba.fastjson2.JSON;
import com.old.silence.job.server.common.dto.RetryAlarmInfo;
import com.old.silence.job.server.common.triple.ImmutableTriple;
import com.old.silence.job.server.domain.model.RetrySceneConfig;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public abstract class AbstractRetryAlarm<E extends ApplicationEvent> extends AbstractAlarm<E, RetryAlarmInfo> {

    @Override
    protected Map<BigInteger, List<RetryAlarmInfo>> convertAlarmDTO(List<RetryAlarmInfo> retryAlarmInfoList, Set<Integer> notifyScene) {

        Map<BigInteger, List<RetryAlarmInfo>> retryAlarmInfoMap = new HashMap<>();
        // 重试任务查询场景告警通知
        Set<String> groupNames = new HashSet<>(), sceneNames = new HashSet<>(), namespaceIds = new HashSet<>();
        for (RetryAlarmInfo retryAlarmInfo : retryAlarmInfoList) {
            groupNames.add(retryAlarmInfo.getGroupName());
            sceneNames.add(retryAlarmInfo.getSceneName());
            namespaceIds.add(retryAlarmInfo.getNamespaceId());
            notifyScene.add(Integer.valueOf(retryAlarmInfo.getNotifyScene().getValue()));
        }

        // 按组名、场景名、命名空间分组
        Map<ImmutableTriple<String, String, String>, RetrySceneConfig> retrySceneConfigMap = accessTemplate.getSceneConfigAccess()
                .getSceneConfigByGroupNameAndSceneNameList(
                groupNames, sceneNames, namespaceIds)
                .stream().collect(Collectors.toMap(i -> ImmutableTriple.of(
                        i.getGroupName(),
                        i.getSceneName(),
                        i.getNamespaceId()),
                Function.identity()
        ));

        for (RetryAlarmInfo retryAlarmInfo : retryAlarmInfoList) {
            RetrySceneConfig retrySceneConfig = retrySceneConfigMap.get(ImmutableTriple.of(
                    retryAlarmInfo.getGroupName(),
                    retryAlarmInfo.getSceneName(),
                    retryAlarmInfo.getNamespaceId()));
            if (Objects.isNull(retrySceneConfig)) {
                continue;
            }

            Set<BigInteger> retryNotifyIds = StringUtils.isBlank(retrySceneConfig.getNotifyIds()) ?
                    new HashSet<>() : new HashSet<>(JSON.parseArray(retrySceneConfig.getNotifyIds(), BigInteger.class));

            for (BigInteger retryNotifyId : retryNotifyIds) {
                List<RetryAlarmInfo> retryAlarmInfos = retryAlarmInfoMap.getOrDefault(retryNotifyId, new ArrayList<>());
                retryAlarmInfos.add(retryAlarmInfo);
                retryAlarmInfoMap.put(retryNotifyId, retryAlarmInfos);
            }
        }

        return retryAlarmInfoMap;
    }
}
