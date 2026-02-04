package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.domain.model.RetrySceneConfig;

public interface RetrySceneConfigDao extends BaseMapper<RetrySceneConfig> {

}
