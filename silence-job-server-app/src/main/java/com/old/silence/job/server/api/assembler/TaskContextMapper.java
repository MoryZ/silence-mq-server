package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.common.server.dto.RetryTaskDTO;
import com.old.silence.job.server.dto.RetrySaveRequestCommand;
import com.old.silence.job.server.retry.task.support.generator.retry.TaskContext;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface TaskContextMapper extends Converter<RetrySaveRequestCommand, TaskContext.TaskInfo> {
    TaskContextMapper INSTANCE = Mappers.getMapper(TaskContextMapper.class);

    @Override
    TaskContext.TaskInfo convert(RetrySaveRequestCommand retrySaveRequestCommand);

    TaskContext.TaskInfo convert(RetryTaskDTO retry);
}
