package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.Workflow;

import java.util.List;

/**
 * <p>
 * 工作流 Mapper 接口
 * </p>
 *
 */
public interface WorkflowDao extends BaseMapper<Workflow> {

    int updateBatchNextTriggerAtById(@Param("list") List<Workflow> list);
}
