package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.JobTask;

import java.util.List;

/**
 * <p>
 * 任务实例 Mapper 接口
 * </p>
 *
 */
public interface JobTaskDao extends BaseMapper<JobTask> {

    int insertBatch(@Param("list") List<JobTask> list);
}
