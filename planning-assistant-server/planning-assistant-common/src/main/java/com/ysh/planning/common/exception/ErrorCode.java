package com.ysh.planning.common.exception;

import lombok.Getter;

@Getter
/** 定义客户端可识别的通用业务失败类别。 */
public enum ErrorCode {

    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
