package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.Namespace;
import com.old.silence.job.server.dto.NamespaceCommand;
import com.old.silence.job.server.vo.NamespaceResponseVO;



@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface NamespaceMapper extends Converter<NamespaceCommand, Namespace> {

    NamespaceResponseVO convert(Namespace namespace);

    @Override
    Namespace convert(NamespaceCommand command);
}
