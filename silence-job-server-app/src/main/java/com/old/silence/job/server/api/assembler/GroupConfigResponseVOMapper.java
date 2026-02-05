package com.old.silence.job.server.api.assembler;


import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.vo.GroupConfigResponseVO;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface GroupConfigResponseVOMapper extends Converter<GroupConfig, GroupConfigResponseVO> {


    @Override
    @Mapping(target = "idGeneratorModeName", expression = "java(toIdGeneratorModeName(groupConfig))")
    GroupConfigResponseVO convert(GroupConfig groupConfig);


    default String toIdGeneratorModeName(GroupConfig groupConfig) {
        if (groupConfig.getIdGeneratorMode() != null) {
            return groupConfig.getIdGeneratorMode().getDescription();
        }
        return StringUtils.EMPTY;

    }



}
