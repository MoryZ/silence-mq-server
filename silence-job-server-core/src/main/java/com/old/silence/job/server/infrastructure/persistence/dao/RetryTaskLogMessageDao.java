package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.RetryTaskLogMessage;

import java.util.List;

/**
 * <p>
 * 重试日志异常信息记录表 Mapper 接口
 * </p>
 *
 */
public interface RetryTaskLogMessageDao extends BaseMapper<RetryTaskLogMessage> {

    int insertBatch(@Param("list") List<RetryTaskLogMessage> list);

    int updateBatch(@Param("list") List<RetryTaskLogMessage> list);

}
