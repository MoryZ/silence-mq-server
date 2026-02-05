package com.old.silence.job.server.api.assembler;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.Retry;
import com.old.silence.job.server.vo.RetryResponseVO;

import java.time.Instant;
import java.util.Objects;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface RetryTaskResponseVOMapper extends Converter<Retry, RetryResponseVO> {

    @Override
    @Mapping(target = "nextTriggerAt", expression = "java(toLocalDateTime(retry.getNextTriggerAt()))")
    RetryResponseVO convert(Retry retry);


    default Instant toLocalDateTime(Long nextTriggerAt) {
        if (Objects.isNull(nextTriggerAt) || nextTriggerAt == 0) {
            return null;
        }

        return Instant.ofEpochMilli(nextTriggerAt);
    }
}
