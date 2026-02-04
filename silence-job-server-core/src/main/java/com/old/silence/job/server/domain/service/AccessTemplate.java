package com.old.silence.job.server.domain.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import com.old.silence.job.common.enums.OperationTypeEnum;
import com.old.silence.job.server.domain.model.GroupConfig;
import com.old.silence.job.server.domain.model.NotifyConfig;
import com.old.silence.job.server.domain.model.Retry;
import com.old.silence.job.server.domain.model.RetryDeadLetter;
import com.old.silence.job.server.domain.model.RetrySceneConfig;
import com.old.silence.job.server.domain.model.RetryTask;
import com.old.silence.job.server.domain.service.config.ConfigAccess;
import com.old.silence.job.server.domain.service.config.GroupConfigAccess;
import com.old.silence.job.server.domain.service.config.NotifyConfigAccess;
import com.old.silence.job.server.domain.service.config.SceneConfigAccess;
import com.old.silence.job.server.domain.service.task.RetryDeadLetterTaskAccess;
import com.old.silence.job.server.domain.service.task.RetryTaskAccess;
import com.old.silence.job.server.domain.service.task.TaskAccess;
import com.old.silence.job.server.infrastructure.exception.SilenceJobDatasourceException;

/**
 * 数据处理模板类
 *
 */
@Component
public class AccessTemplate {
    protected Map<String, Access> REGISTER_ACCESS = new HashMap<>();

    public AccessTemplate(List<Access> accesses) {

        for (Access access : accesses) {
            for (OperationTypeEnum typeEnum : OperationTypeEnum.values()) {
                if (access.supports(typeEnum)) {
                    REGISTER_ACCESS.put(typeEnum.name(), access);
                    break;
                }
            }
        }
    }

    /**
     * 获取重试任务操作类
     *
     * @return {@link RetryTaskAccess} 重试任务操作类
     */
    public TaskAccess<RetryTask> getRetryTaskAccess() {
        return (TaskAccess<RetryTask>) Optional.ofNullable(REGISTER_ACCESS.get(OperationTypeEnum.RETRY_TASK.name()))
                .orElseThrow(() -> new SilenceJobDatasourceException("not supports operation type"));
    }

    /**
     * 获取重试任务操作类
     *
     * @return {@link RetryTaskAccess} 重试任务操作类
     */
    public TaskAccess<Retry> getRetryAccess() {
        return (TaskAccess<Retry>) Optional.ofNullable(REGISTER_ACCESS.get(OperationTypeEnum.RETRY.name()))
                .orElseThrow(() -> new SilenceJobDatasourceException("not supports operation type"));
    }

    /**
     * 获取死信任务操作类
     *
     * @return {@link RetryDeadLetterTaskAccess} 获取死信任务操作类
     */
    public TaskAccess<RetryDeadLetter> getRetryDeadLetterAccess() {
        return (TaskAccess<RetryDeadLetter>) Optional.ofNullable(
                        REGISTER_ACCESS.get(OperationTypeEnum.RETRY_DEAD_LETTER.name()))
                .orElseThrow(() -> new SilenceJobDatasourceException("not supports operation type"));

    }

    /**
     * 获取场景配置操作类
     *
     * @return {@link SceneConfigAccess} 获取场景配置操作类
     */
    public ConfigAccess<RetrySceneConfig> getSceneConfigAccess() {
        return (ConfigAccess<RetrySceneConfig>) Optional.ofNullable(REGISTER_ACCESS.get(OperationTypeEnum.SCENE.name()))
                .orElseThrow(() -> new SilenceJobDatasourceException("not supports operation type"));

    }

    /**
     * 获取组配置操作类
     *
     * @return {@link GroupConfigAccess} 获取组配置操作类
     */
    public ConfigAccess<GroupConfig> getGroupConfigAccess() {
        return (ConfigAccess<GroupConfig>) Optional.ofNullable(REGISTER_ACCESS.get(OperationTypeEnum.GROUP.name()))
                .orElseThrow(() -> new SilenceJobDatasourceException("not supports operation type"));

    }

    /**
     * 获取通知配置操作类
     *
     * @return {@link NotifyConfigAccess} 获取通知配置操作类
     */
    public ConfigAccess<NotifyConfig> getNotifyConfigAccess() {
        return (ConfigAccess<NotifyConfig>) Optional.ofNullable(REGISTER_ACCESS.get(OperationTypeEnum.NOTIFY.name()))
                .orElseThrow(() -> new SilenceJobDatasourceException("not supports operation type"));

    }

}
