package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.vo.DashboardCardResponseDO;
import com.old.silence.job.server.vo.DashboardCardResponseVO;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface RetrySummaryResponseVOMapper extends Converter<DashboardCardResponseDO.RetryTask, DashboardCardResponseVO.RetryTask> {


    @Override
    DashboardCardResponseVO.RetryTask convert(DashboardCardResponseDO.RetryTask retryTask);
}
