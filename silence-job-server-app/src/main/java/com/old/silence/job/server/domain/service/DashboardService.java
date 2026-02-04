package com.old.silence.job.server.domain.service;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.old.silence.core.util.CollectionUtils;
import com.old.silence.job.common.enums.NodeType;
import com.old.silence.job.common.enums.SystemModeEnum;
import com.old.silence.job.common.enums.SystemTaskType;
import com.old.silence.job.common.util.StreamUtils;
import com.old.silence.job.server.api.assembler.DashboardLineResponseVOMapper;
import com.old.silence.job.server.api.assembler.JobSummaryResponseVOMapper;
import com.old.silence.job.server.api.assembler.RetrySummaryResponseVOMapper;
import com.old.silence.job.server.api.assembler.SceneQuantityRankResponseVOMapper;
import com.old.silence.job.server.api.config.TenantContext;
import com.old.silence.job.server.common.register.ServerRegister;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.domain.model.JobSummary;
import com.old.silence.job.server.domain.model.RetrySceneConfig;
import com.old.silence.job.server.domain.model.RetrySummary;
import com.old.silence.job.server.domain.model.ServerNode;
import com.old.silence.job.server.dto.JobLineQueryVo;
import com.old.silence.job.server.dto.LineQueryVO;
import com.old.silence.job.server.enums.DateTypeEnum;
import com.old.silence.job.server.infrastructure.persistence.dao.JobSummaryDao;
import com.old.silence.job.server.infrastructure.persistence.dao.RetrySummaryDao;
import com.old.silence.job.server.infrastructure.persistence.dao.ServerNodeDao;
import com.old.silence.job.server.vo.ActivePodQuantityResponseDO;
import com.old.silence.job.server.vo.DashboardCardResponseDO;
import com.old.silence.job.server.vo.DashboardCardResponseVO;
import com.old.silence.job.server.vo.DashboardLineResponseDO;
import com.old.silence.job.server.vo.DashboardLineResponseVO;
import com.old.silence.job.server.vo.DashboardRetryLineResponseDO;
import com.old.silence.job.server.vo.DashboardRetryLineResponseVO;
import com.old.silence.page.PageImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class DashboardService {

    private final ServerNodeDao serverNodeDao;
    private final JobSummaryDao jobSummaryDao;
    private final RetrySummaryDao retrySummaryDao;

    private final RetrySummaryResponseVOMapper retrySummaryResponseVOMapper;
    private final JobSummaryResponseVOMapper jobSummaryResponseVOMapper;
    private final SceneQuantityRankResponseVOMapper sceneQuantityRankResponseVOMapper;
    private final DashboardLineResponseVOMapper dashboardLineResponseVOMapper;

    public DashboardService(ServerNodeDao serverNodeDao, JobSummaryDao jobSummaryDao,
                            RetrySummaryDao retrySummaryDao, RetrySummaryResponseVOMapper retrySummaryResponseVOMapper,
                            JobSummaryResponseVOMapper jobSummaryResponseVOMapper,
                            SceneQuantityRankResponseVOMapper sceneQuantityRankResponseVOMapper,
                            DashboardLineResponseVOMapper dashboardLineResponseVOMapper) {
        this.serverNodeDao = serverNodeDao;
        this.jobSummaryDao = jobSummaryDao;
        this.retrySummaryDao = retrySummaryDao;
        this.retrySummaryResponseVOMapper = retrySummaryResponseVOMapper;
        this.jobSummaryResponseVOMapper = jobSummaryResponseVOMapper;
        this.sceneQuantityRankResponseVOMapper = sceneQuantityRankResponseVOMapper;
        this.dashboardLineResponseVOMapper = dashboardLineResponseVOMapper;
    }


    public DashboardCardResponseVO taskRetryJob() {

        // 查询登录用户权限
        DashboardCardResponseVO responseVO = new DashboardCardResponseVO();

        // 重试任务
        DashboardCardResponseDO.RetryTask retryTaskDO = retrySummaryDao.selectRetryTask(
                new LambdaQueryWrapper<>()
        );
        DashboardCardResponseVO.RetryTask retryTaskVO = retrySummaryResponseVOMapper.convert(retryTaskDO);
        responseVO.setRetryTask(retryTaskVO);

        // 定时任务
        DashboardCardResponseDO.JobTask jobTaskDO = jobSummaryDao.selectJobTask(
                new LambdaQueryWrapper<JobSummary>()
                        .eq(JobSummary::getSystemTaskType, SystemTaskType.JOB.getValue())
        );
        DashboardCardResponseVO.JobTask jobTaskVO = jobSummaryResponseVOMapper.convert(jobTaskDO);
        responseVO.setJobTask(jobTaskVO);

        // 工作流任务
        DashboardCardResponseDO.JobTask workFlowTaskDO = jobSummaryDao.selectJobTask(
                new LambdaQueryWrapper<JobSummary>()
                        .eq(JobSummary::getSystemTaskType, SystemTaskType.WORKFLOW.getValue())
        );
        DashboardCardResponseVO.WorkFlowTask workFlowTaskVO = jobSummaryResponseVOMapper.convertToWorkFlowTask(workFlowTaskDO);
        responseVO.setWorkFlowTask(workFlowTaskVO);

        // 重试任务柱状图
        HashMap<Instant, DashboardCardResponseVO.RetryTaskBar> retryTaskBarMap = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            DashboardCardResponseVO.RetryTaskBar retryTaskBar = new DashboardCardResponseVO.RetryTaskBar();
            retryTaskBar.setX(LocalDateTime.of(LocalDate.now(), LocalTime.MIN)
                    .plusDays(-i).toLocalDate().toString());
            retryTaskBar.setTaskTotal(0L);
            retryTaskBarMap.put(LocalDateTimeUtil.beginOfDay(LocalDateTime.now().minusDays(i)).toInstant(ZoneOffset.UTC), retryTaskBar);
        }
        List<DashboardCardResponseDO.RetryTask> retryTaskList = retrySummaryDao.selectRetryTaskBarList(
                new LambdaQueryWrapper<RetrySummary>()
                        .orderByDesc(RetrySummary::getId));
        Map<Instant, LongSummaryStatistics> summaryStatisticsMap = retryTaskList.stream()
                .collect(Collectors.groupingBy(DashboardCardResponseDO.RetryTask::getTriggerAt,
                        Collectors.summarizingLong(i -> i.getMaxCountNum() + i.getRunningNum() + i.getSuspendNum() + i.getFinishNum())));
        for (Map.Entry<Instant, LongSummaryStatistics> map : summaryStatisticsMap.entrySet()) {
            if (retryTaskBarMap.containsKey(LocalDateTime.of(LocalDate.ofInstant(map.getKey(), ZoneId.systemDefault()), LocalTime.MIN).toInstant(ZoneOffset.UTC))) {
                DashboardCardResponseVO.RetryTaskBar retryTaskBar = retryTaskBarMap.get(LocalDateTimeUtil.beginOfDay(LocalDateTime.ofInstant(map.getKey(), ZoneId.systemDefault())).toInstant(ZoneOffset.UTC));
                retryTaskBar.setX(map.getKey().toString());
                retryTaskBar.setTaskTotal(map.getValue().getSum());
            }
        }
        responseVO.setRetryTaskBarList(new ArrayList<>(retryTaskBarMap.values()));

        // 在线Pods
        List<ActivePodQuantityResponseDO> activePodQuantityDO = serverNodeDao.selectActivePodCount(List.of(TenantContext.getTenantId(), ServerRegister.NAMESPACE_ID));
        Map<NodeType, Long> map = StreamUtils.toMap(activePodQuantityDO,
                ActivePodQuantityResponseDO::getNodeType, ActivePodQuantityResponseDO::getTotal);
        Long clientTotal = map.getOrDefault(NodeType.CLIENT, 0L);
        Long serverTotal = map.getOrDefault(NodeType.SERVER, 0L);
        responseVO.getOnLineService().setServerTotal(serverTotal);
        responseVO.getOnLineService().setClientTotal(clientTotal);
        responseVO.getOnLineService().setTotal(clientTotal + serverTotal);

        return responseVO;

    }


    public DashboardRetryLineResponseVO retryLineList(Page<Object> page, LineQueryVO queryVO) {
        // 查询登录用户权限
        DashboardRetryLineResponseVO responseVO = new DashboardRetryLineResponseVO();

        // 重试任务列表
        LambdaQueryWrapper<RetrySceneConfig> wrapper = new LambdaQueryWrapper<>();

        // 针对 Group By 分页自定义countStatement
        page.setSearchCount(false);
        page.setTotal(retrySummaryDao.selectRetryTaskListCount(wrapper));

        IPage<DashboardRetryLineResponseDO.Task> resultPage = retrySummaryDao.selectRetryTaskList(wrapper, page);
        List<DashboardRetryLineResponseVO.Task> taskList = CollectionUtils.transformToList(resultPage.getRecords(), jobSummaryResponseVOMapper::convert);

        IPage<DashboardRetryLineResponseVO.Task> responsePage = new PageImpl<>(taskList, resultPage.getTotal());
        responseVO.setTaskList(responsePage);

        // 折线图
        DateTypeEnum dateTypeEnum = DateTypeEnum.valueOf(queryVO.getType());
        Instant startDateTime = Instant.now();
        Instant endDateTime = Instant.now();
        List<DashboardLineResponseDO> dashboardRetryLinkeResponseDOList = retrySummaryDao.selectRetryLineList(
                "dateFormat",
                new LambdaQueryWrapper<RetrySummary>()
                        .eq(StrUtil.isNotBlank(queryVO.getGroupName()), RetrySummary::getGroupName, queryVO.getGroupName())
                        .between(RetrySummary::getTriggerAt, startDateTime, endDateTime));
        List<DashboardLineResponseVO> dashboardLineResponseVOList = CollectionUtils.transformToList(dashboardRetryLinkeResponseDOList, dashboardLineResponseVOMapper::convert);
        dateTypeEnum.getConsumer().accept(dashboardLineResponseVOList);
        dashboardLineResponseVOList.sort(Comparator.comparing(DashboardLineResponseVO::getCreatedDate));
        responseVO.setDashboardLineResponseDOList(dashboardLineResponseVOList);

        // 排行榜
        List<DashboardRetryLineResponseDO.Rank> rankList = retrySummaryDao.selectDashboardRankList(
                new LambdaQueryWrapper<RetrySummary>()
                        .ge(RetrySummary::getTriggerAt, startDateTime)
                        .le(RetrySummary::getTriggerAt, endDateTime)
                        .groupBy(RetrySummary::getNamespaceId, RetrySummary::getGroupName, RetrySummary::getSceneName));
        List<DashboardRetryLineResponseVO.Rank> ranks = CollectionUtils.transformToList(rankList, sceneQuantityRankResponseVOMapper::convert);
        responseVO.setRankList(ranks);
        return responseVO;
    }


    public DashboardRetryLineResponseVO jobLineList(Page<Object> pager, JobLineQueryVo queryVO) {
        // 查询登录用户权限
        List<String> groupNames = List.of();
        DashboardRetryLineResponseVO responseVO = new DashboardRetryLineResponseVO();

        // 任务类型
        SystemTaskType systemTaskType = SystemModeEnum.JOB.equals(queryVO.getMode()) ? SystemTaskType.JOB : SystemTaskType.WORKFLOW;
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<Job>()
                .in(CollectionUtils.isNotEmpty(groupNames), Job::getGroupName, groupNames);

        // 针对 Group By 分页自定义 countStatement
        pager.setSearchCount(false);
        pager.setTotal(SystemModeEnum.JOB.equals(queryVO.getMode()) ?
                jobSummaryDao.selectJobTaskListCount(wrapper) :
                jobSummaryDao.selectWorkflowTaskListCount(wrapper));

        IPage<DashboardRetryLineResponseDO.Task> taskIPage = SystemModeEnum.JOB.equals(queryVO.getMode()) ?
                jobSummaryDao.selectJobTaskList(wrapper, pager) : jobSummaryDao.selectWorkflowTaskList(wrapper, pager);
        List<DashboardRetryLineResponseVO.Task> taskList = CollectionUtils.transformToList(taskIPage.getRecords(), jobSummaryResponseVOMapper::convert);
        IPage<DashboardRetryLineResponseVO.Task> page = new PageImpl<>(taskList, taskIPage.getTotal());
        responseVO.setTaskList(page);

        // 折线图
        DateTypeEnum dateTypeEnum = DateTypeEnum.valueOf(queryVO.getType());
        Instant startDateTime = Instant.now();
        Instant endDateTime = Instant.now();
        List<DashboardLineResponseDO> dashboardLineResponseDOList = jobSummaryDao.selectJobLineList(
                "dateFormat",
                new LambdaQueryWrapper<JobSummary>()
                        .in(CollectionUtils.isNotEmpty(groupNames), JobSummary::getGroupName, groupNames)
                        .eq(StrUtil.isNotBlank(queryVO.getGroupName()), JobSummary::getGroupName, queryVO.getGroupName())
                        .eq(JobSummary::getSystemTaskType, systemTaskType)
                        .between(JobSummary::getTriggerAt, startDateTime, endDateTime));
        List<DashboardLineResponseVO> dashboardLineResponseVOList = CollectionUtils.transformToList(dashboardLineResponseDOList, dashboardLineResponseVOMapper::convert);
        dateTypeEnum.getConsumer().accept(dashboardLineResponseVOList);
        dashboardLineResponseVOList.sort(Comparator.comparing(DashboardLineResponseVO::getCreatedDate));
        responseVO.setDashboardLineResponseDOList(dashboardLineResponseVOList);

        // 排行榜
        List<DashboardRetryLineResponseDO.Rank> rankList = jobSummaryDao.selectDashboardRankList(
                systemTaskType,
                new LambdaQueryWrapper<JobSummary>()
                        .in(CollectionUtils.isNotEmpty(groupNames), JobSummary::getGroupName, groupNames)
                        .eq(StrUtil.isNotBlank(queryVO.getGroupName()), JobSummary::getGroupName, queryVO.getGroupName())
                        .ge(JobSummary::getTriggerAt, startDateTime).le(JobSummary::getTriggerAt, endDateTime)
                        .eq(JobSummary::getSystemTaskType, systemTaskType)
                        .groupBy(JobSummary::getNamespaceId, JobSummary::getGroupName, JobSummary::getBusinessId));
        List<DashboardRetryLineResponseVO.Rank> ranks = CollectionUtils.transformToList(rankList, sceneQuantityRankResponseVOMapper::convert);
        responseVO.setRankList(ranks);
        return responseVO;
    }


}
