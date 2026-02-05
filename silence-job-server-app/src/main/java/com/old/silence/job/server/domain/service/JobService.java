package com.old.silence.job.server.domain.service;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.HashUtil;
import cn.hutool.core.util.StrUtil;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.common.enums.JobTaskExecutorScene;
import com.old.silence.job.common.enums.SystemTaskType;
import com.old.silence.job.common.util.StreamUtils;
import com.old.silence.job.server.api.assembler.JobMapper;
import com.old.silence.job.server.api.assembler.JobResponseVOMapper;
import com.old.silence.job.server.common.WaitStrategy;
import com.old.silence.job.server.common.config.SystemProperties;
import com.old.silence.job.server.common.dto.PartitionTask;
import com.old.silence.job.server.common.strategy.WaitStrategies;
import com.old.silence.job.server.common.util.CronUtils;
import com.old.silence.job.server.common.util.DateUtils;
import com.old.silence.job.server.common.util.PartitionTaskUtils;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.domain.model.Job;
import com.old.silence.job.server.domain.model.JobSummary;
import com.old.silence.job.server.dto.ExportJobVO;
import com.old.silence.job.server.dto.JobTriggerVO;
import com.old.silence.job.server.exception.SilenceJobServerException;
import com.old.silence.job.server.handler.GroupHandler;
import com.old.silence.job.server.infrastructure.persistence.dao.GroupConfigDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobDao;
import com.old.silence.job.server.infrastructure.persistence.dao.JobSummaryDao;
import com.old.silence.job.server.infrastructure.persistence.dao.SystemUserDao;
import com.old.silence.job.server.job.task.dto.JobTaskPrepareDTO;
import com.old.silence.job.server.job.task.support.JobPrepareHandler;
import com.old.silence.job.server.job.task.support.JobTaskConverter;
import com.old.silence.job.server.job.task.support.cache.ResidentTaskCache;
import com.old.silence.job.server.vo.JobResponseVO;

import com.old.silence.core.util.CollectionUtils;


@Service
@Validated
public class JobService {

    private final SystemProperties systemProperties;
    private final JobDao jobDao;
    private final JobPrepareHandler terminalJobPrepareHandler;
    private final GroupConfigDao groupConfigDao;
    private final GroupHandler groupHandler;
    private final JobSummaryDao jobSummaryDao;
    private final SystemUserDao systemUserDao;
    private final JobResponseVOMapper jobResponseVOMapper;
    private final JobMapper jobMapper;

    public JobService(SystemProperties systemProperties, JobDao jobDao,
                      JobPrepareHandler terminalJobPrepareHandler, GroupConfigDao groupConfigDao,
                      GroupHandler groupHandler, JobSummaryDao jobSummaryDao,
                      SystemUserDao systemUserDao, JobResponseVOMapper jobResponseVOMapper, JobMapper jobMapper) {
        this.systemProperties = systemProperties;
        this.jobDao = jobDao;
        this.terminalJobPrepareHandler = terminalJobPrepareHandler;
        this.groupConfigDao = groupConfigDao;
        this.groupHandler = groupHandler;
        this.jobSummaryDao = jobSummaryDao;
        this.systemUserDao = systemUserDao;
        this.jobResponseVOMapper = jobResponseVOMapper;
        this.jobMapper = jobMapper;
    }

    private static Long calculateNextTriggerAt(Job job, Long time) {
        if (Objects.equals(job.getTriggerType().getValue(), SystemConstants.WORKFLOW_TRIGGER_TYPE.byteValue())) {
            return 0L;
        }

        WaitStrategy waitStrategy = WaitStrategies.WaitStrategyEnum.getWaitStrategy(job.getTriggerType().getValue());
        WaitStrategies.WaitStrategyContext waitStrategyContext = new WaitStrategies.WaitStrategyContext();
        waitStrategyContext.setTriggerInterval(job.getTriggerInterval());
        waitStrategyContext.setNextTriggerAt(time);
        return waitStrategy.computeTriggerTime(waitStrategyContext);
    }

    
    public IPage<JobResponseVO> queryPage(Page<Job> page, QueryWrapper<Job> queryWrapper) {
        Page<Job> jobPage = jobDao.selectPage(page, queryWrapper);

        return jobPage.convert(jobResponseVOMapper::convert);

    }

    
    public JobResponseVO findById(BigInteger id) {
        Job job = jobDao.selectById(id);
        return jobResponseVOMapper.convert(job);
    }

    
    public List<Instant> getTimeByCron(String cron) {
        return CronUtils.getExecuteTimeByCron(cron, 5);
    }

    
    public boolean create(Job job) {
        // 判断常驻任务
        job.setResident(isResident(job));
        job.setBucketIndex(HashUtil.bkdrHash(job.getGroupName() + job.getJobName())
                % systemProperties.getBucketTotal());
        job.setNextTriggerAt(calculateNextTriggerAt(job, DateUtils.toNowMilli()));
        job.setId(null);
        return 1 == jobDao.insert(job);
    }

    
    public boolean update(Job job) {

        Job jobDb = jobDao.selectById(job.getId());

        // 判断常驻任务
        job.setResident(isResident(job));
        // 工作流任务
        if (Objects.equals(job.getTriggerType().getValue(), SystemConstants.WORKFLOW_TRIGGER_TYPE.byteValue())) {
            job.setNextTriggerAt(0L);
            // 非常驻任务 > 非常驻任务
        } else if (!jobDb.getResident() && ! job.getResident()) {
            job.setNextTriggerAt(calculateNextTriggerAt(job, DateUtils.toNowMilli()));
        } else if (jobDb.getResident() && !job.getResident()) {
            // 常驻任务的触发时间
            long time = Optional.ofNullable(ResidentTaskCache.get(job.getId()))
                    .orElse(DateUtils.toNowMilli());
            job.setNextTriggerAt(calculateNextTriggerAt(job, time));
            // 老的是不是常驻任务 新的是常驻任务 需要使用当前时间计算下次触发时间
        } else if (!jobDb.getResident() && job.getResident()) {
            job.setNextTriggerAt(DateUtils.toNowMilli());
        }

        // 禁止更新组
        job.setGroupName(null);
        return 1 == jobDao.updateById(job);
    }

    private Boolean isResident(Job job) {
        if (Objects.equals(job.getTriggerType().getValue().intValue(), SystemConstants.WORKFLOW_TRIGGER_TYPE)) {
            return false;
        }

        if (job.getTriggerType().getValue().intValue() == WaitStrategies.WaitStrategyEnum.FIXED.getValue()) {
            return Integer.parseInt(job.getTriggerInterval()) < 10;
        } else if (job.getTriggerType().getValue().intValue() == WaitStrategies.WaitStrategyEnum.CRON.getValue()) {
            return CronUtils.getExecuteInterval(job.getTriggerInterval()) < 10 * 1000;
        } else {
            throw new SilenceJobServerException("未知触发类型");
        }
    }

    
    public int updateJobStatus(BigInteger id, boolean status) {
        return jobDao.updateStatusById(status, id);
    }

    public boolean trigger(BigInteger id, JobTriggerVO jobTrigger) {
        Job job = jobDao.selectById(id);
        Assert.notNull(job, () -> new SilenceJobServerException("job can not be null."));

        long count = groupConfigDao.selectCount(new LambdaQueryWrapper<GroupConfig>()
                .eq(GroupConfig::getGroupName, job.getGroupName())
                .eq(GroupConfig::getGroupStatus, true)
        );

        Assert.isTrue(count > 0,
                () -> new SilenceJobServerException("组:[{}]已经关闭，不支持手动执行.", job.getGroupName()));
        JobTaskPrepareDTO jobTaskPrepare = JobTaskConverter.INSTANCE.toJobTaskPrepare(job);
        // 设置now表示立即执行
        jobTaskPrepare.setNextTriggerAt(DateUtils.toNowMilli());
        jobTaskPrepare.setTaskExecutorScene(JobTaskExecutorScene.MANUAL_JOB);
        if (StrUtil.isNotBlank(jobTrigger.getTmpArgsStr())) {
            jobTaskPrepare.setTmpArgsStr(jobTrigger.getTmpArgsStr());
        }
        // 创建批次
        terminalJobPrepareHandler.handle(jobTaskPrepare);

        return Boolean.TRUE;
    }

    
    public List<JobResponseVO> getJobList(QueryWrapper<Job> queryWrapper) {
        List<Job> jobs = jobDao.selectList(queryWrapper);
        return  CollectionUtils.transformToList(jobs, jobResponseVOMapper::convert);
    }

    @Transactional(rollbackFor = Exception.class)
    public void importJobs(List<Job> jobs) {
        groupHandler.validateGroupExistence(
                StreamUtils.toSet(jobs, Job::getGroupName)
        );
        jobs.forEach(this::create);
    }

    
    public String exportJobs(ExportJobVO exportJobVO) {

        List<Job> requestList = new ArrayList<>();
        PartitionTaskUtils.process(startId -> {
                    List<Job> jobList = jobDao.selectPage(new PageDTO<>(0, 100),
                            new LambdaQueryWrapper<Job>()
                                    .eq(StrUtil.isNotBlank(exportJobVO.getGroupName()), Job::getGroupName, exportJobVO.getGroupName())
                                    .likeRight(StrUtil.isNotBlank(exportJobVO.getJobName()), Job::getJobName, StrUtil.trim(exportJobVO.getJobName()))
                                    .eq(Objects.nonNull(exportJobVO.getJobStatus()), Job::getJobStatus, exportJobVO.getJobStatus())
                                    .in(CollectionUtils.isNotEmpty(exportJobVO.getJobIds()), Job::getId, exportJobVO.getJobIds())
                                    .gt(Job::getId, startId)
                                    .orderByAsc(Job::getId)
                    ).getRecords();
                    return StreamUtils.toList(jobList, JobPartitionTask::new);
                },
                partitionTasks -> {
                    List<JobPartitionTask> jobPartitionTasks = (List<JobPartitionTask>) partitionTasks;
                    requestList.addAll(
                            CollectionUtils.transformToList(jobPartitionTasks, JobPartitionTask::getJob));
                }, 0);

        return JSON.toJSONString(requestList);
    }

    
    @Transactional
    public Boolean deleteJobByIds(Set<BigInteger> ids) {

        Assert.isTrue(ids.size() == jobDao.delete(
                new LambdaQueryWrapper<Job>()
                        .eq(Job::getJobStatus, true)
                        .in(Job::getId, ids)
        ), () -> new SilenceJobServerException("删除定时任务失败, 请检查任务状态是否关闭状态"));

        List<JobSummary> jobSummaries = jobSummaryDao.selectList(new LambdaQueryWrapper<JobSummary>()
                .select(JobSummary::getId)
                .in(JobSummary::getBusinessId, ids)
                .eq(JobSummary::getSystemTaskType, SystemTaskType.JOB.getValue())
        );
        if (CollectionUtils.isNotEmpty(jobSummaries)) {
            jobSummaryDao.deleteBatchIds(StreamUtils.toSet(jobSummaries, JobSummary::getId));
        }

        return Boolean.TRUE;
    }

    
    
    private static class JobPartitionTask extends PartitionTask {

        // 这里就直接放GroupConfig为了后面若加字段不需要再这里在调整了
        private final Job job;

        public JobPartitionTask(@NotNull Job job) {
            this.job = job;
            setId(job.getId());
        }

        public Job getJob() {
            return job;
        }
    }

}
