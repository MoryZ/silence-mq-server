package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.SequenceAlloc;

/**
 * <p>
 * 号段模式序号ID分配表 Mapper 接口
 * </p>
 *
 */
public interface SequenceAllocDao extends BaseMapper<SequenceAlloc> {

}
