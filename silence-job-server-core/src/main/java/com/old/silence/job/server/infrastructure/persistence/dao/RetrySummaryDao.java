package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.old.silence.job.server.domain.model.RetrySceneConfig;
import com.old.silence.job.server.domain.model.RetrySummary;
import com.old.silence.job.server.vo.DashboardCardResponseDO;
import com.old.silence.job.server.vo.DashboardLineResponseDO;
import com.old.silence.job.server.vo.DashboardRetryLineResponseDO;

import java.util.List;


public interface RetrySummaryDao extends BaseMapper<RetrySummary> {

    int insertBatch(@Param("list") List<RetrySummary> list);

    int updateBatch(@Param("list") List<RetrySummary> list);

    DashboardCardResponseDO.RetryTask selectRetryTask(@Param("ew") Wrapper<RetrySummary> wrapper);

    List<DashboardCardResponseDO.RetryTask> selectRetryTaskBarList(@Param("ew") Wrapper<RetrySummary> wrapper);

    IPage<DashboardRetryLineResponseDO.Task> selectRetryTaskList(@Param("ew") Wrapper<RetrySceneConfig> wrapper, Page<Object> page);

    long selectRetryTaskListCount(@Param("ew") Wrapper<RetrySceneConfig> wrapper);

    List<DashboardLineResponseDO> selectRetryLineList(@Param("dateFormat") String dateFormat, @Param("ew") Wrapper<RetrySummary> wrapper);

    List<DashboardRetryLineResponseDO.Rank> selectDashboardRankList(@Param("ew") Wrapper<RetrySummary> wrapper);

}
