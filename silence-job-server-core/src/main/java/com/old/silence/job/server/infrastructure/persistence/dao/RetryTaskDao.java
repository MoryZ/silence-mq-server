package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.RetryTask;

public interface RetryTaskDao extends BaseMapper<RetryTask> {

}
