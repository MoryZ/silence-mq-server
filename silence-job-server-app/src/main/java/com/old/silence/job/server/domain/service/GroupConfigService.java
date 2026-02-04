package com.old.silence.job.server.domain.service;



import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.google.common.collect.Lists;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.util.StreamUtils;


import com.old.silence.job.server.api.assembler.GroupConfigResponseVOMapper;
import com.old.silence.job.server.api.config.TenantContext;
import com.old.silence.job.server.common.dto.PartitionTask;
import com.old.silence.job.server.common.handler.ConfigVersionSyncHandler;
import com.old.silence.job.server.common.util.PartitionTaskUtils;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.domain.model.Namespace;
import com.old.silence.job.server.domain.model.RetrySceneConfig;
import com.old.silence.job.server.domain.model.ServerNode;
import com.old.silence.job.server.domain.model.SystemUserPermission;
import com.old.silence.job.server.domain.model.Workflow;
import com.old.silence.job.server.dto.ExportGroupCommand;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.infrastructure.persistence.dao.GroupConfigDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobDao;
import com.old.silence.job.server.infrastructure.persistence.dao.NamespaceDao;
import com.old.silence.job.server.infrastructure.persistence.dao.RetrySceneConfigDao;
import com.old.silence.job.server.infrastructure.persistence.dao.ServerNodeDao;
import com.old.silence.job.server.infrastructure.persistence.dao.SystemUserPermissionDao;
import com.old.silence.job.server.infrastructure.persistence.dao.WorkflowDao;
import com.old.silence.job.server.vo.GroupConfigResponseVO;


/**
 * CRUD 组、场景、通知
 *
 */
@Service
public class GroupConfigService {

    private final ServerNodeDao serverNodeDao;
    private final GroupConfigDao groupConfigDao;
    private final RetrySceneConfigDao retrySceneConfigDao;
    private final ConfigVersionSyncHandler configVersionSyncHandler;
    private final JdbcTemplate jdbcTemplate;
    private final NamespaceDao namespaceDao;
    private final JobDao jobDao;
    private final WorkflowDao workflowDao;
    private final SystemUserPermissionDao systemUserPermissionDao;
    private final GroupConfigResponseVOMapper groupConfigResponseVOMapper;

    public GroupConfigService(ServerNodeDao serverNodeDao, GroupConfigDao groupConfigDao, RetrySceneConfigDao retrySceneConfigDao,
                              ConfigVersionSyncHandler configVersionSyncHandler, JdbcTemplate jdbcTemplate,
                              NamespaceDao namespaceDao, JobDao jobDao, WorkflowDao workflowDao,
                              SystemUserPermissionDao systemUserPermissionDao,
                              GroupConfigResponseVOMapper groupConfigResponseVOMapper) {
        this.serverNodeDao = serverNodeDao;
        this.groupConfigDao = groupConfigDao;
        this.retrySceneConfigDao = retrySceneConfigDao;
        this.configVersionSyncHandler = configVersionSyncHandler;
        this.jdbcTemplate = jdbcTemplate;
        this.namespaceDao = namespaceDao;
        this.jobDao = jobDao;
        this.workflowDao = workflowDao;
        this.systemUserPermissionDao = systemUserPermissionDao;
        this.groupConfigResponseVOMapper = groupConfigResponseVOMapper;
    }


    @Transactional
    public Boolean create(GroupConfig groupConfig) {


        Assert.isTrue(groupConfigDao.selectCount(new LambdaQueryWrapper<GroupConfig>()
                        .eq(GroupConfig::getGroupName, groupConfig.getGroupName())) == 0,
                () -> new SilenceJobServerException("GroupName已经存在 {}", groupConfig.getGroupName()));

        // 保存组配置
        return doSaveGroupConfig(groupConfig);
    }


    
    @Transactional
    public Boolean updateGroup(GroupConfig groupConfig) {

        List<Integer> tablePartitionList = getTablePartitionList();
        if (CollectionUtils.isEmpty(tablePartitionList)) {
            return Boolean.FALSE;
        }

        String groupName = groupConfig.getGroupName();

        
        long count = groupConfigDao.selectCount(
                new LambdaQueryWrapper<GroupConfig>()
                        .eq(GroupConfig::getGroupName, groupName));
        if (count <= 0) {
            return false;
        }

        groupConfig.setDescription(Optional.ofNullable(groupConfig.getDescription()).orElse(StrUtil.EMPTY));
        // 使用@TableField(value = "version", update= "%s+1") 进行更新version, 这里必须初始化一个值
        groupConfig.setVersion(1);
        Assert.isTrue(tablePartitionList.contains(groupConfig.getGroupPartition()),
                () -> new SilenceJobServerException("分区不存在. [{}]", tablePartitionList));
        Assert.isTrue(groupConfig.getGroupPartition() >= 0,
                () -> new SilenceJobServerException("分区不能是负数."));

        Assert.isTrue(1 == groupConfigDao.update(groupConfig,
                        new LambdaUpdateWrapper<GroupConfig>()
                                .eq(GroupConfig::getGroupName, groupName)),
                () -> new SilenceJobServerException("exception occurred while adding group. groupConfigVO[{}]",
                        groupConfig));

        // 同步版本， 版本为0代表需要同步到客户端
        var namespaceId = TenantContext.getTenantId();
        boolean add = configVersionSyncHandler.addSyncTask(groupName, namespaceId, 0);
        // 若添加失败则强制发起同步
        if (!add) {
            configVersionSyncHandler.syncVersion(groupName, namespaceId);
        }
        return Boolean.TRUE;
    }

    public Boolean updateGroupStatus(String groupName, Boolean status) {
        GroupConfig groupConfig = new GroupConfig();
        groupConfig.setGroupStatus(status);
        
        return groupConfigDao.update(groupConfig,
                new LambdaUpdateWrapper<GroupConfig>()
                        .eq(GroupConfig::getGroupName, groupName)) == 1;
    }

    public IPage<GroupConfigResponseVO> queryPage(Page<GroupConfig> page, QueryWrapper<GroupConfig> queryWrapper) {

        Page<GroupConfig> groupConfigPageDTO = groupConfigDao.selectPage(
                page, queryWrapper);
        return groupConfigPageDTO.convert(groupConfigResponseVOMapper::convert);

    }

    private boolean doSaveGroupConfig(GroupConfig groupConfig) {
        groupConfig.setVersion(1);
        groupConfig.setGroupName(groupConfig.getGroupName());
        groupConfig.setToken(groupConfig.getToken());
        groupConfig.setDescription(Optional.ofNullable(groupConfig.getDescription()).orElse(StrUtil.EMPTY));
        
        Assert.isTrue(1 == groupConfigDao.insert(groupConfig),
                () -> new SilenceJobServerException("新增组异常异常 groupConfigVO[{}]", groupConfig));

        return Boolean.TRUE;
    }

    public GroupConfigResponseVO getGroupConfigByGroupName(String groupName) {

        var groupConfig = groupConfigDao.selectOne(
                new LambdaQueryWrapper<GroupConfig>()
                        .eq(GroupConfig::getGroupName, groupName));
        return groupConfigResponseVOMapper.convert(groupConfig);
    }

    
    public List<GroupConfigResponseVO> getAllGroupConfigList(List<String> namespaceIds) {

        List<GroupConfig> groupConfigs = groupConfigDao.selectList(
                new LambdaQueryWrapper<GroupConfig>()
                        .select(GroupConfig::getGroupName, GroupConfig::getNamespaceId)
                        .in(CollectionUtils.isNotEmpty(namespaceIds), GroupConfig::getNamespaceId, namespaceIds));
        if (CollectionUtils.isEmpty(groupConfigs)) {
            return Collections.emptyList();
        }

        List<Namespace> namespaces = namespaceDao.selectList(
                new LambdaQueryWrapper<Namespace>()
                        .in(Namespace::getUniqueId, StreamUtils.toSet(groupConfigs, GroupConfig::getNamespaceId)));

        Map<String, String> namespaceMap = StreamUtils.toMap(namespaces, Namespace::getUniqueId, Namespace::getName);

        List<GroupConfigResponseVO> groupConfigResponses = CollectionUtils.transformToList(groupConfigs,
                groupConfigResponseVOMapper::convert);
        for (GroupConfigResponseVO groupConfigResponseVO : groupConfigResponses) {
            groupConfigResponseVO.setNamespaceName(namespaceMap.get(groupConfigResponseVO.getNamespaceId()));
        }

        return groupConfigResponses;
    }

    
    public List<GroupConfig> getAllGroupNameList() {
        return groupConfigDao.selectList(new LambdaQueryWrapper<>());
    }

    public List<String> getOnlinePods(String groupName) {
        List<ServerNode> serverNodes = serverNodeDao.selectList(
                new LambdaQueryWrapper<ServerNode>()
                        .eq(ServerNode::getGroupName, groupName));
        return StreamUtils.toList(serverNodes, serverNode -> serverNode.getHostIp() + ":" + serverNode.getHostPort());
    }

    
    public List<Integer> getTablePartitionList() {
        DataSource dataSource = jdbcTemplate.getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            String tableNamePattern = "sj_retry_task_%";

            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tableRs = metaData.getTables(catalog, schema, tableNamePattern, new String[]{"TABLE"});

            // 获取表名
            List<String> tableList = new ArrayList<>();
            while (tableRs.next()) {
                String tableName = tableRs.getString("TABLE_NAME");
                tableList.add(tableName);
            }

            return tableList.stream()
                    .map(ReUtil::getFirstNumber)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (SQLException ignored) {
        }

        return Collections.emptyList();
    }

    
    @Transactional
    public void importGroup(List<GroupConfig> requestList) {

        Set<String> groupSet = StreamUtils.toSet(requestList, GroupConfig::getGroupName);
        

        List<GroupConfig> configs = groupConfigDao.selectList(new LambdaQueryWrapper<GroupConfig>()
                .select(GroupConfig::getGroupName)
                .in(GroupConfig::getGroupName, groupSet));

        Assert.isTrue(CollectionUtils.isEmpty(configs),
                () -> new SilenceJobServerException("导入失败. 原因: 组{}已存在", StreamUtils.toSet(configs, GroupConfig::getGroupName)));

        for (GroupConfig groupConfig : requestList) {

            // 保存组配置
            doSaveGroupConfig(groupConfig);

        }

    }

    public String exportGroup(ExportGroupCommand exportGroupCommand) {
        var groupIds = exportGroupCommand.getGroupIds();
        var groupStatus = exportGroupCommand.getGroupStatus();
        var groupName = exportGroupCommand.getGroupName();

        List<GroupConfig> allRequestList = Lists.newArrayList();
        PartitionTaskUtils.process((startId -> {
            List<GroupConfig> groupConfigs = groupConfigDao.selectPage(new PageDTO<>(0, 100),
                    new LambdaQueryWrapper<GroupConfig>()
                            .ge(GroupConfig::getId, startId)
                            .eq(Objects.nonNull(groupStatus), GroupConfig::getGroupStatus, groupStatus)
                            .in(CollectionUtils.isNotEmpty(groupIds), GroupConfig::getId, groupIds)
                            .likeRight(StrUtil.isNotBlank(groupName), GroupConfig::getGroupName, StrUtil.trim(groupName))
                            .orderByAsc(GroupConfig::getId)
            ).getRecords();
            return groupConfigs.stream().map(GroupConfigPartitionTask::new).collect(Collectors.toList());
        }), partitionTasks -> {
            List<GroupConfigPartitionTask> configPartitionTasks = (List<GroupConfigPartitionTask>) partitionTasks;
            List<GroupConfig> configs = StreamUtils.toList(configPartitionTasks, GroupConfigPartitionTask::getConfig);
            allRequestList.addAll(configs);
        }, 0);

        return JSON.toJSONString(allRequestList);
    }

    
    public boolean deleteByGroupName(String groupName) {
        // 前置检查
        // 1. 定时任务是否删除
        Assert.isTrue(CollectionUtils.isEmpty(jobDao.selectList(new LambdaQueryWrapper<Job>()
                        .eq(Job::getGroupName, groupName).orderByAsc(Job::getId))),
                () -> new SilenceJobServerException("存在未删除的定时任务. 请先删除当前组的定时任务后再重试删除"));
        // 2. 工作流是否删除
        Assert.isTrue(CollectionUtils.isEmpty(workflowDao.selectList(new LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getGroupName, groupName).orderByAsc(Workflow::getId))),
                () -> new SilenceJobServerException("存在未删除的工作流任务. 请先删除当前组的工作流任务后再重试删除"));
        // 3. 重试场景是否删除
        Assert.isTrue(CollectionUtils.isEmpty(retrySceneConfigDao.selectPage(new PageDTO<>(1, 1), new LambdaQueryWrapper<RetrySceneConfig>()
                        .eq(RetrySceneConfig::getGroupName, groupName).orderByAsc(RetrySceneConfig::getId)).getRecords()),
                () -> new SilenceJobServerException("存在未删除的重试场景. 请先删除当前组的重试场景后再重试删除"));
        // 4. 是否存在已分配的权限
        Assert.isTrue(CollectionUtils.isEmpty(systemUserPermissionDao.selectList(new LambdaQueryWrapper<SystemUserPermission>()
                        .eq(SystemUserPermission::getGroupName, groupName).orderByAsc(SystemUserPermission::getId))),
                () -> new SilenceJobServerException("存在已分配组权限. 请先删除已分配的组权限后再重试删除"));
        // 5. 检查是否存活的客户端节点
        Assert.isTrue(CollectionUtils.isEmpty(serverNodeDao.selectList(new LambdaQueryWrapper<ServerNode>()
                        .eq(ServerNode::getGroupName, groupName).orderByAsc(ServerNode::getId))),
                () -> new SilenceJobServerException("存在存活中客户端节点."));

        Assert.isTrue(1 == groupConfigDao.delete(
                        new LambdaQueryWrapper<GroupConfig>()
                                .eq(GroupConfig::getGroupStatus, false)
                                .eq(GroupConfig::getGroupName, groupName)),
                () -> new SilenceJobServerException("删除组失败, 请检查状态是否关闭状态"));

        return Boolean.TRUE;
    }

    
    
    private static class GroupConfigPartitionTask extends PartitionTask {
        // 这里就直接放GroupConfig为了后面若加字段不需要再这里在调整了
        private final GroupConfig config;

        public GroupConfigPartitionTask(GroupConfig config) {
            this.config = config;
            setId(config.getId());
        }

        public GroupConfig getConfig() {
            return config;
        }
    }
}
