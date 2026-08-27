package com.ysh.planning.plan.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetItemWithNameDto {

    private Long id;
    private Long planId;
    private Long categoryId;
    private BigDecimal amount;
    private Integer sortOrder;
    private String categoryName;
}
