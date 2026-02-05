package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.vo.DashboardLineResponseDO;
import com.old.silence.job.server.vo.DashboardLineResponseVO;

/**
 * @author moryzang
 */
@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface DashboardLineResponseVOMapper extends Converter<DashboardLineResponseDO, DashboardLineResponseVO> {


    @Override
    DashboardLineResponseVO convert(DashboardLineResponseDO dashboardLineResponseDOs);
}