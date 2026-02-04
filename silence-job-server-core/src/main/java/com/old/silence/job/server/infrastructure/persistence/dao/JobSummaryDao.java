package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.old.silence.job.common.enums.SystemTaskType;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.domain.model.JobSummary;
import com.old.silence.job.server.vo.DashboardCardResponseDO;
import com.old.silence.job.server.vo.DashboardLineResponseDO;
import com.old.silence.job.server.vo.DashboardRetryLineResponseDO;


import java.util.List;


public interface JobSummaryDao extends BaseMapper<JobSummary> {

    int insertBatch(@Param("list") List<JobSummary> list);

    int updateBatch(@Param("list") List<JobSummary> list);

    IPage<DashboardRetryLineResponseDO.Task> selectJobTaskList(@Param("ew") Wrapper<Job> wrapper, Page<Object> page);

    // jobTaskList 自定义 countStatement
    long selectJobTaskListCount(@Param("ew") Wrapper<Job> wrapper);

    IPage<DashboardRetryLineResponseDO.Task> selectWorkflowTaskList(@Param("ew") Wrapper<Job> wrapper, Page<Object> page);

    // workflowTaskList 自定义 countStatement
    long selectWorkflowTaskListCount(@Param("ew") Wrapper<Job> wrapper);

    List<DashboardLineResponseDO> selectJobLineList(@Param("dateFormat") String dateFormat, @Param("ew") Wrapper<JobSummary> wrapper);

    List<DashboardRetryLineResponseDO.Rank> selectDashboardRankList(@Param("systemTaskType") SystemTaskType systemTaskType, @Param("ew") Wrapper<JobSummary> wrapper);

    DashboardCardResponseDO.JobTask selectJobTask(@Param("ew") Wrapper<JobSummary> wrapper);

}
