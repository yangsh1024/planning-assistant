package com.ysh.planning.webauth.service;

/**
 * 登录页展示的六位数字码格式化工具。
 */
public final class WebLoginCode {
    private WebLoginCode() {
    }

    public static String format(int value) {
        if (value < 0 || value > 999_999) {
            throw new IllegalArgumentException("login code must be between 0 and 999999");
        }
        return String.format("%06d", value);
    }
}
