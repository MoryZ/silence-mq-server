package com.old.silence.job.server.handler;


import cn.hutool.core.lang.Assert;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Sets;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.util.StreamUtils;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.GroupConfigDao;


@Component
public class GroupHandler {

    private final GroupConfigDao groupConfigDao;

    public GroupHandler(GroupConfigDao groupConfigDao) {
        this.groupConfigDao = groupConfigDao;
    }

    /**
     * 校验组是否存在
     *
     * @param groupNameSet 待校验的组
     */
    public void validateGroupExistence(Set<String> groupNameSet) {
        Assert.notEmpty(groupNameSet, () -> new SilenceJobServerException("组不能为空"));
        List<GroupConfig> groupConfigs = groupConfigDao
                .selectList(new LambdaQueryWrapper<GroupConfig>()
                        .select(GroupConfig::getGroupName)
                        .in(GroupConfig::getGroupName, groupNameSet)
                );

        Set<String> notExistedGroupNameSet = Sets.difference(groupNameSet,
                StreamUtils.toSet(groupConfigs, GroupConfig::getGroupName));

        Assert.isTrue(CollectionUtils.isEmpty(notExistedGroupNameSet),
                () -> new SilenceJobServerException("组:{}不存在", notExistedGroupNameSet));
    }

}
