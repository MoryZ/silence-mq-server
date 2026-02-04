package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.RetryDeadLetter;

import java.util.List;

public interface RetryDeadLetterDao extends BaseMapper<RetryDeadLetter> {

    int insertBatch(@Param("list") List<RetryDeadLetter> list);

}
