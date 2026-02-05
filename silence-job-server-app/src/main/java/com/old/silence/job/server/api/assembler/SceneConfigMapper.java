package com.old.silence.job.server.api.assembler;

import cn.hutool.core.util.StrUtil;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.core.convert.converter.Converter;
import com.alibaba.fastjson2.JSON;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.server.domain.model.RetrySceneConfig;
import com.old.silence.job.server.dto.SceneConfigCommand;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface SceneConfigMapper extends Converter<SceneConfigCommand, RetrySceneConfig> {


    @Override
    @Mapping(target = "notifyIds", expression = "java(toNotifyIdsStr(sceneConfigCommand.getNotifyIds()))")
    RetrySceneConfig convert(SceneConfigCommand sceneConfigCommand);

    @Mapping(target = "notifyIds", expression = "java(toNotifyIds(requestVO.getNotifyIds()))")
    SceneConfigCommand toSceneConfigRequestVO(RetrySceneConfig requestVO);

    default Set<BigInteger> toNotifyIds(String notifyIds) {
        if (StrUtil.isBlank(notifyIds)) {
            return new HashSet<>();
        }

        return new HashSet<>(JSON.parseArray(notifyIds, BigInteger.class));
    }

    default String toNotifyIdsStr(Set<BigInteger> notifyIds) {
        if (CollectionUtils.isEmpty(notifyIds)) {
            return StrUtil.EMPTY;
        }

        return JSON.toJSONString(notifyIds);
    }
}
