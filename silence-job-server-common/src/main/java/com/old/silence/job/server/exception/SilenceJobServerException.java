package com.old.silence.job.server.exception;


import com.old.silence.job.common.exception.BaseSilenceJobException;

/**
 * 服务端异常类
 *
 */
public class SilenceJobServerException extends BaseSilenceJobException {

    public SilenceJobServerException(String message) {
        super(message);
    }

    public SilenceJobServerException(String message, Object... arguments) {
        super(message, arguments);
    }
}
