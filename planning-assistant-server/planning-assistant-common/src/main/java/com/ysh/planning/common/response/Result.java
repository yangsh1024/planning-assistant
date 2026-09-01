package com.ysh.planning.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 所有 REST 接口统一使用的业务响应外层结构。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    /**
     * 创建携带业务数据的成功响应。
     *
     * @param data 响应数据
     * @return 成功响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 创建不携带业务数据的成功响应。
     *
     * @return 成功响应
     */
    public static Result<Void> ok() {
        return new Result<>(200, "success", null);
    }

    /**
     * 创建携带业务错误码的失败响应。
     *
     * @param code 业务错误码
     * @param message 面向客户端的失败说明
     * @return 失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
