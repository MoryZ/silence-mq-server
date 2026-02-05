package com.old.silence.job.server.api.assembler;


import org.mapstruct.Mapper;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.SystemUser;
import com.old.silence.job.server.dto.UserSessionVO;
import com.old.silence.job.server.vo.SystemUserResponseVO;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface SystemUserResponseVOMapper extends Converter<SystemUser, SystemUserResponseVO> {

    SystemUserResponseVO convert(UserSessionVO systemUser);

    @Override
    SystemUserResponseVO convert(SystemUser systemUser);
}
