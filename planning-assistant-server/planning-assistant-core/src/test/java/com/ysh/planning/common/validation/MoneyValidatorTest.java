package com.ysh.planning.common.validation;

import com.ysh.planning.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyValidatorTest {

    @Test
    void acceptsPositiveAmountsStoredByDecimalTwelveTwo() {
        assertEquals(new BigDecimal("9999999999.99"), MoneyValidator.parsePositiveAmount("9999999999.99"));
        assertEquals(new BigDecimal("0.10"), MoneyValidator.parsePositiveAmount("0.10"));
    }

    @Test
    void rejectsValuesOutsideTheAmountContract() {
        assertThrows(BizException.class, () -> MoneyValidator.parsePositiveAmount("1.001"));
        assertThrows(BizException.class, () -> MoneyValidator.parsePositiveAmount("10000000000.00"));
        assertThrows(BizException.class, () -> MoneyValidator.parsePositiveAmount("0.00"));
        assertThrows(BizException.class, () -> MoneyValidator.parsePositiveAmount("1e2"));
    }
}
