package com.old.silence.job.server.common.appender;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import cn.hutool.core.util.StrUtil;

import org.slf4j.MDC;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.common.context.SilenceSpringContext;
import com.old.silence.job.common.util.NetUtils;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.log.constant.LogFieldConstants;
import com.old.silence.job.log.dto.LogContentDTO;
import com.old.silence.job.log.enums.LogTypeEnum;
import com.old.silence.job.server.common.LogStorage;
import com.old.silence.job.server.common.config.SystemProperties;
import com.old.silence.job.server.common.dto.JobLogMetaDTO;
import com.old.silence.job.server.common.dto.LogMetaDTO;
import com.old.silence.job.server.common.dto.RetryLogMetaDTO;
import com.old.silence.job.server.common.log.LogStorageFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SilenceJobServerLogbackAppender<E> extends UnsynchronizedAppenderBase<E> {

    @Override
    protected void append(E eventObject) {

        // Not job context
        if (!(eventObject instanceof LoggingEvent) || Objects.isNull(
                MDC.getMDCAdapter().get(LogFieldConstants.MDC_REMOTE))) {
            return;
        }

        LoggingEvent event = (LoggingEvent) eventObject;
        MDC.getMDCAdapter().remove(LogFieldConstants.MDC_REMOTE);
        // Prepare processing
        event.prepareForDeferredProcessing();

        LogContentDTO logContentDTO = new LogContentDTO();
        logContentDTO.addLevelField(event.getLevel().levelStr);
        logContentDTO.addThreadField(event.getThreadName());
        logContentDTO.addLocationField(getLocationField(event));
        logContentDTO.addThrowableField(getThrowableField(event));
        logContentDTO.addHostField(NetUtils.getLocalIpStr());
        logContentDTO.addPortField(SilenceSpringContext.getBean(SystemProperties.class).getServerPort());

        LogMetaDTO logMetaDTO = null;
        try {
            String patternString = "<\\|>(.*?)<\\|>";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(event.getFormattedMessage());
            while (matcher.find()) {
                String extractedData = matcher.group(1);
                if (StrUtil.isBlank(extractedData)) {
                    continue;
                }

                JSONObject jsonObject = JSONObject.parseObject(extractedData);
                if (!jsonObject.containsKey(SystemConstants.JSON_FILED_LOG_TYPE)) {
                    return;
                }

                String name = jsonObject.getString(SystemConstants.JSON_FILED_LOG_TYPE);
                if (LogTypeEnum.RETRY.equals(LogTypeEnum.valueOf(name))) {
                    logMetaDTO = JSON.parseObject(extractedData, RetryLogMetaDTO.class);
                } else if (LogTypeEnum.JOB.equals(LogTypeEnum.valueOf(name))) {
                    logMetaDTO = JSON.parseObject(extractedData, JobLogMetaDTO.class);
                } else {
                    throw new IllegalArgumentException("logType is not support");
                }

                String message = event.getFormattedMessage().replaceFirst(patternString, StrUtil.EMPTY);
                logContentDTO.addMessageField(message);
                logContentDTO.addTimeStamp(Optional.ofNullable(logMetaDTO.getTimestamp()).orElse(event.getTimeStamp()));
                break;
            }

            if (Objects.isNull(logMetaDTO)) {
                return;
            }

            // 保存执行的日志
            saveLog(logContentDTO, logMetaDTO);

        } catch (Exception e) {
            SilenceJobLog.LOCAL.error("日志解析失败. msg:[{}]", event.getFormattedMessage(), e);
        }

    }

    /**
     * 保存日志
     *
     * @param logContentDTO 日志内容
     * @param logMetaDTO    日志元数据
     */
    private void saveLog(LogContentDTO logContentDTO, LogMetaDTO logMetaDTO) {

        LogStorage logStorage = LogStorageFactory.get(logMetaDTO.getLogType());
        if (Objects.nonNull(logStorage)) {
            logStorage.storage(logContentDTO, logMetaDTO);
        }
    }

    private String getThrowableField(LoggingEvent event) {
        IThrowableProxy iThrowableProxy = event.getThrowableProxy();
        if (iThrowableProxy != null) {
            String throwable = getExceptionInfo(iThrowableProxy);
            throwable += formatThrowable(event.getThrowableProxy().getStackTraceElementProxyArray());
            return throwable;
        }
        return null;
    }

    private String getLocationField(LoggingEvent event) {
        StackTraceElement[] caller = event.getCallerData();
        if (caller != null && caller.length > 0) {
            return caller[0].toString();
        }
        return null;
    }

    private String formatThrowable(StackTraceElementProxy[] stackTraceElementProxyArray) {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElementProxy step : stackTraceElementProxyArray) {
            builder.append(CoreConstants.LINE_SEPARATOR);
            String string = step.toString();
            builder.append(CoreConstants.TAB).append(string);
            ThrowableProxyUtil.subjoinPackagingData(builder, step);
        }
        return builder.toString();
    }

    private String getExceptionInfo(IThrowableProxy iThrowableProxy) {
        String s = iThrowableProxy.getClassName();
        String message = iThrowableProxy.getMessage();
        return (message != null) ? (s + ": " + message) : s;
    }
}
