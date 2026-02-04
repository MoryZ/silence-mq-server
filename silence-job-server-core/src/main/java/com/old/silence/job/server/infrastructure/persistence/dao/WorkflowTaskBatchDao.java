package com.old.silence.job.server.infrastructure.persistence.dao;


import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.old.silence.job.server.domain.model.WorkflowTaskBatch;
import com.old.silence.job.server.vo.WorkflowBatchResponseDO;

import java.util.List;

/**
 * <p>
 * 工作流批次 Mapper 接口
 * </p>
 *
 */
public interface WorkflowTaskBatchDao extends BaseMapper<WorkflowTaskBatch> {

    List<WorkflowBatchResponseDO> selectWorkflowBatchPageList(Page<WorkflowTaskBatch> pageDTO, @Param("ew") Wrapper<WorkflowTaskBatch> wrapper);

    List<WorkflowBatchResponseDO> selectWorkflowBatchList(@Param("ew") Wrapper<WorkflowTaskBatch> wrapper);
}
