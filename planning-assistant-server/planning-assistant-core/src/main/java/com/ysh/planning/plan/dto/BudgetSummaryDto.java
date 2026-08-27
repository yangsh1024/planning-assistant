package com.ysh.planning.plan.dto;

import lombok.Data;

import java.util.List;

@Data
public class BudgetSummaryDto {

    private String yearMonth;
    private String totalBudget;
    private String totalActual;
    private String totalRemaining;
    private Double rate;
    private List<BudgetSummaryItemDto> items;
}
