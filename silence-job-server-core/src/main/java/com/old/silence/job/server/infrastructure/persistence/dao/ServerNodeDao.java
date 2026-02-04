package com.old.silence.job.server.infrastructure.persistence.dao;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.old.silence.job.server.domain.model.ServerNode;
import com.old.silence.job.server.vo.ActivePodQuantityResponseDO;

import java.util.List;

public interface ServerNodeDao extends BaseMapper<ServerNode> {


    @Select("<script>" +
            "SELECT COUNT(1) AS total, node_type AS nodeType " +
            "FROM sj_server_node " +
            "WHERE namespace_id IN " +
            "<foreach collection='namespaceIds' item='namespaceId' open='(' close=')' separator=','>" +
            "   #{namespaceId}" +
            "</foreach>" +
            "GROUP BY node_type" +
            "</script>")
    List<ActivePodQuantityResponseDO> selectActivePodCount(@Param("namespaceIds") List<String> namespaceIds);

    int insertBatch(@Param("list") List<ServerNode> list);

    int updateBatchExpireAt(@Param("list") List<ServerNode> list);


}
