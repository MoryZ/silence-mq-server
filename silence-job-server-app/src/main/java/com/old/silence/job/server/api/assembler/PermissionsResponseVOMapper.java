package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.SystemUserPermission;
import com.old.silence.job.server.vo.PermissionsResponseVO;

@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface PermissionsResponseVOMapper extends Converter<SystemUserPermission, PermissionsResponseVO> {


    @Override
    PermissionsResponseVO convert(SystemUserPermission systemUserPermission);

}
