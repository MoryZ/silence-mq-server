package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.JobLogMessage;

import java.util.List;


public interface JobLogMessageDao extends BaseMapper<JobLogMessage> {

    int insertBatch(@Param("list") List<JobLogMessage> list);

}
