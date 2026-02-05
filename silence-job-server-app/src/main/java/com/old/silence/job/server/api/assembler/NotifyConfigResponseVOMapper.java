package com.old.silence.job.server.api.assembler;

import cn.hutool.core.util.StrUtil;

import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;
import com.alibaba.fastjson2.JSON;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.NotifyConfig;
import com.old.silence.job.server.vo.NotifyConfigResponseVO;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface NotifyConfigResponseVOMapper extends Converter<NotifyConfig, NotifyConfigResponseVO> {


    @Override
    @Mapping(target = "recipientIds", expression = "java(toNotifyRecipientIds(notifyConfig.getRecipientIds()))")
    NotifyConfigResponseVO convert(NotifyConfig notifyConfig);

    default Set<BigInteger> toNotifyRecipientIds(String notifyRecipientIdsStr) {
        if (StringUtils.isBlank(notifyRecipientIdsStr)) {
            return new HashSet<>();
        }

        return new HashSet<>(JSON.parseArray(notifyRecipientIdsStr, BigInteger.class));
    }
}
