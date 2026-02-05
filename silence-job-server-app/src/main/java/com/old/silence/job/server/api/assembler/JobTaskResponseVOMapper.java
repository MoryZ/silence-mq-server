package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.JobTask;
import com.old.silence.job.server.vo.JobTaskResponseVO;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface JobTaskResponseVOMapper extends Converter<JobTask, JobTaskResponseVO> {


    @Override
    JobTaskResponseVO convert(JobTask jobTasks);
}
