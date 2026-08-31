package com.ysh.planning.common.validation;

import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** 校验金额字符串符合数据库精度约束，并转换为精确的 BigDecimal。 */
public final class MoneyValidator {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d{1,10}(?:\\.\\d{1,2})?$");

    private MoneyValidator() {
    }

    /**
     * 解析一笔正数金额。
     * <ol><li>校验格式</li><li>校验正数</li></ol>
     * @param value 外部提交的金额字符串
     * @return 满足两位小数约束的精确金额
     * @throws BizException 金额格式或数值不合法时抛出
     */
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
