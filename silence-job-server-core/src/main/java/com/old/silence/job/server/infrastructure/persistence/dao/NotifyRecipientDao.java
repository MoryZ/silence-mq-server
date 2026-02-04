package com.old.silence.job.server.infrastructure.persistence.dao;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.NotifyRecipient;

/**
 * <p>
 * 告警通知接收人 Mapper 接口
 * </p>
 *
 */
public interface NotifyRecipientDao extends BaseMapper<NotifyRecipient> {

}
