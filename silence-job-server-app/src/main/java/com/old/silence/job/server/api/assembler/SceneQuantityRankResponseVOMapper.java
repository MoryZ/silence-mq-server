package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.vo.DashboardRetryLineResponseDO;
import com.old.silence.job.server.vo.DashboardRetryLineResponseVO;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface SceneQuantityRankResponseVOMapper extends Converter<DashboardRetryLineResponseDO.Rank, DashboardRetryLineResponseVO.Rank> {


    @Override
    DashboardRetryLineResponseVO.Rank convert(DashboardRetryLineResponseDO.Rank rank);

    DashboardRetryLineResponseVO.Task convert(DashboardRetryLineResponseDO.Task task);
}
