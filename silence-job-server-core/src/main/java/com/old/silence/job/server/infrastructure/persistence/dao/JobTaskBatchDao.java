package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.old.silence.job.server.domain.model.JobTaskBatch;
import com.old.silence.job.server.domain.model.WorkflowTaskBatch;
import com.old.silence.job.server.vo.JobBatchResponseDO;
import com.old.silence.job.server.vo.JobBatchSummaryResponseDO;

import java.util.List;


public interface JobTaskBatchDao extends BaseMapper<JobTaskBatch> {

    List<JobBatchResponseDO> selectJobBatchPageList(IPage<JobTaskBatch> iPage, @Param("ew") Wrapper<JobTaskBatch> wrapper);

    List<JobBatchResponseDO> selectJobBatchListByIds(@Param("ew") Wrapper<JobTaskBatch> wrapper);

    List<JobBatchSummaryResponseDO> selectJobBatchSummaryList(@Param("ew") Wrapper<JobTaskBatch> wrapper);

    List<JobBatchSummaryResponseDO> selectWorkflowTaskBatchSummaryList(@Param("ew") Wrapper<WorkflowTaskBatch> wrapper);
}
