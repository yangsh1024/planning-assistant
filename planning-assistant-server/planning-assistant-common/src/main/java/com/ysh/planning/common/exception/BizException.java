package com.ysh.planning.common.exception;

import lombok.Getter;

/** 携带业务错误码的受控异常，由统一异常处理器转换为公开响应。 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
