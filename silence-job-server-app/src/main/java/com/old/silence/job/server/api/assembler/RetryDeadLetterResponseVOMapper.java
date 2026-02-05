package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.RetryDeadLetter;
import com.old.silence.job.server.vo.RetryDeadLetterResponseVO;



@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface RetryDeadLetterResponseVOMapper extends Converter<RetryDeadLetter, RetryDeadLetterResponseVO> {


    @Override
    RetryDeadLetterResponseVO convert(RetryDeadLetter retryDeadLetter);

}
