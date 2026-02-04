package com.old.silence.job.server.job.task.support.expression;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.old.silence.job.common.constant.SystemConstants;
import com.old.silence.job.server.exception.SilenceJobServerException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;


public class ExpressionInvocationHandler implements InvocationHandler {

    private final Object expressionEngine;

    public ExpressionInvocationHandler(Object expressionEngine) {
        this.expressionEngine = expressionEngine;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {

        try {
            Object[] expressionParams = (Object[]) args[1];
            String params = (String) expressionParams[0];
            Map<String, Object> contextMap = new HashMap<>();
            var type = new TypeReference<Map<String, Object>>() {
            }.getType();
            if (StrUtil.isNotBlank(params)) {
                try {
                    contextMap = JSON.parseObject(params, type);
                } catch (Exception e) {
                    contextMap.put(SystemConstants.SINGLE_PARAM, params);
                }
            }

            args[1] = new Object[]{contextMap};
            return method.invoke(expressionEngine, args);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            throw new SilenceJobServerException(targetException.getMessage());
        } catch (Exception e) {
            throw new SilenceJobServerException("表达式执行失败", e);
        }
    }
}
