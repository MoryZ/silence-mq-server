package com.old.silence.job.server.api.assembler;

import cn.hutool.core.util.StrUtil;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;
import com.alibaba.fastjson2.JSON;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.dto.JobCommand;

import java.math.BigInteger;
import java.util.Set;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface JobMapper extends Converter<JobCommand, Job> {

    @Override
    @Mapping(target = "notifyIds", expression = "java(toNotifyIdsStr(jobCommand.getNotifyIds()))")
    Job convert(JobCommand jobCommand);


    default String toNotifyIdsStr(Set<BigInteger> notifyIds) {
        if (CollectionUtils.isEmpty(notifyIds)) {
            return StrUtil.EMPTY;
        }

        return JSON.toJSONString(notifyIds);
    }
}
