package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.vo.DashboardCardResponseDO;
import com.old.silence.job.server.vo.DashboardCardResponseVO;
import com.old.silence.job.server.vo.DashboardRetryLineResponseDO;
import com.old.silence.job.server.vo.DashboardRetryLineResponseVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface JobSummaryResponseVOMapper extends Converter<DashboardCardResponseDO.JobTask, DashboardCardResponseVO.JobTask> {


    @Override
    @Mapping(target = "successRate", expression = "java(toSuccessRate(jobTask.getSuccessNum(), jobTask.getTotalNum()))")
    DashboardCardResponseVO.JobTask convert(DashboardCardResponseDO.JobTask jobTask);

    @Mapping(target = "successRate", expression = "java(toSuccessRate(jobTask.getSuccessNum(), jobTask.getTotalNum()))")
    DashboardCardResponseVO.WorkFlowTask convertToWorkFlowTask(DashboardCardResponseDO.JobTask jobTask);

    DashboardRetryLineResponseVO.Task convert(DashboardRetryLineResponseDO.Task task);

    default BigDecimal toSuccessRate(Integer successNum, Integer totalNum) {
        if (Objects.isNull(totalNum) || totalNum == 0) {
            return null;
        }
        return new BigDecimal(String.valueOf(successNum)).divide(new BigDecimal(String.valueOf(totalNum)), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
    }
}
