package com.old.silence.job.server.infrastructure.persistence.dao;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.Retry;
import com.old.silence.job.server.vo.DashboardRetryResponseDO;

import java.util.List;

public interface RetryDao extends BaseMapper<Retry> {

    int insertBatch(@Param("list") List<Retry> list);

    int updateBatchNextTriggerAtById(@Param("list") List<Retry> list);

    List<DashboardRetryResponseDO> selectRetrySummaryList(@Param("ew") Wrapper<Retry> wrapper);
}
