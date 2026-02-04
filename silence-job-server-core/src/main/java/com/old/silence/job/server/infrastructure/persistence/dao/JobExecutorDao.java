package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.JobExecutor;

/**
 * @author moryzang
 */
public interface JobExecutorDao extends BaseMapper<JobExecutor> {
}
