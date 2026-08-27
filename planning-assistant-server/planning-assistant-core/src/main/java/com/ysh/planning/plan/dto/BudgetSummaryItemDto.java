package com.ysh.planning.plan.dto;

import lombok.Data;

@Data
public class BudgetSummaryItemDto {

    private Long categoryId;
    private String categoryName;
    private String budget;
    private String actual;
    private String remaining;
    private Double rate;
}
