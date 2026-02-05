package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;
import com.alibaba.fastjson2.JSON;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.server.domain.model.NotifyConfig;
import com.old.silence.job.server.dto.NotifyConfigCommand;

import java.util.Set;

@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface NotifyConfigMapper extends Converter<NotifyConfigCommand, NotifyConfig> {


    default String toNotifyRecipientIdsStr(Set<Long> notifyRecipientIds) {
        if (CollectionUtils.isEmpty(notifyRecipientIds)) {
            return null;
        }

        return JSON.toJSONString(notifyRecipientIds);
    }

    @Override
    @Mapping(target = "recipientIds", expression = "java(toNotifyRecipientIdsStr(notifyConfigCommand.getRecipientIds()))")
    NotifyConfig convert(NotifyConfigCommand notifyConfigCommand);
}
