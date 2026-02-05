package com.old.silence.job.server.api.assembler;


import org.mapstruct.Mapper;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.dto.GroupConfigCommand;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface GroupConfigMapper extends Converter<GroupConfigCommand, GroupConfig> {


    @Override
    GroupConfig convert(GroupConfigCommand groupConfigCommand);

}
