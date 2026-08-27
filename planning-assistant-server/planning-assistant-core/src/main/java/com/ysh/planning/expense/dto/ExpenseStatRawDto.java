package com.ysh.planning.expense.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExpenseStatRawDto {

    private Long categoryId;
    private String categoryName;
    private BigDecimal total;
    private Integer count;
}
