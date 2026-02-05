package com.old.silence.job.server.api.assembler;


import cn.hutool.core.util.StrUtil;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;
import com.alibaba.fastjson2.JSON;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.vo.JobResponseVO;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface JobResponseVOMapper extends Converter<Job, JobResponseVO> {


    @Override
    @Mapping(target = "nextTriggerAt", expression = "java(toLocalDateTime(job.getNextTriggerAt()))")
    @Mapping(target = "notifyIds", expression = "java(toNotifyIds(job.getNotifyIds()))")
    JobResponseVO convert(Job job);


    default Set<BigInteger> toNotifyIds(String notifyIds) {
        if (StrUtil.isBlank(notifyIds)) {
            return new HashSet<>();
        }

        return new HashSet<>(JSON.parseArray(notifyIds, BigInteger.class));
    }

    default Instant toLocalDateTime(Long nextTriggerAt) {
        if (Objects.isNull(nextTriggerAt) || nextTriggerAt == 0) {
            return null;
        }

        return Instant.ofEpochMilli(nextTriggerAt);
    }
}
