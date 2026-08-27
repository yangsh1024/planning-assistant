package com.ysh.planning.common.validation;

import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Validates monetary input against the DECIMAL(12,2) storage contract. */
public final class MoneyValidator {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d{1,10}(?:\\.\\d{1,2})?$");

    private MoneyValidator() {
    }

    public static BigDecimal parsePositiveAmount(String value) {
        if (value == null || !AMOUNT_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "金额格式不正确，应为最多两位小数的正数");
        }
        BigDecimal amount = new BigDecimal(value);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "金额必须大于0");
        }
        return amount;
    }
}
