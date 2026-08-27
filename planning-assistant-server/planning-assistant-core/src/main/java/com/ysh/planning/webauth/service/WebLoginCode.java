package com.ysh.planning.webauth.service;

public final class WebLoginCode {

    private WebLoginCode() {
    }

    public static String format(int value) {
        if (value < 0 || value > 999_999) {
            throw new IllegalArgumentException("fallback code must be between 0 and 999999");
        }
        return String.format("%06d", value);
    }
}
