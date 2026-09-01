package com.ysh.planning.common.logging;

/**
 * 为安全日志保留原异常栈帧，同时省略可能含敏感信息的消息和 cause 链。
 */
public final class SafeLogException extends RuntimeException {

    private SafeLogException(String type, StackTraceElement[] stackTrace) {
        super(type, null, true, true);
        setStackTrace(stackTrace);
    }

    public static SafeLogException from(Throwable source) {
        return new SafeLogException(source.getClass().getSimpleName(), source.getStackTrace());
    }
}
