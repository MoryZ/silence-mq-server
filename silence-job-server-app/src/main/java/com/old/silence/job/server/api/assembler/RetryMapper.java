package com.old.silence.job.server.api.assembler;


import org.mapstruct.Mapper;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.Retry;
import com.old.silence.job.server.dto.RetryCommand;
import com.old.silence.job.server.retry.task.dto.RetryTaskPrepareDTO;

@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface RetryMapper extends Converter<RetryCommand, Retry> {

    @Override
    Retry convert(RetryCommand source);

    RetryTaskPrepareDTO toRetryTaskPrepareDTO(Retry retry);


}
