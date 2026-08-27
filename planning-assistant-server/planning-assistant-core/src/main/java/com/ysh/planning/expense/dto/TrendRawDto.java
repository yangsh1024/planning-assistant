package com.ysh.planning.expense.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrendRawDto {

    private String yearMonth;
    private BigDecimal total;
}
