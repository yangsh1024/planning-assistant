package com.ysh.planning.plan.dto;

import lombok.Data;

import java.util.List;

@Data
public class BudgetPlanDto {

    private String yearMonth;
    private String totalBudget;
    private List<BudgetItemDto> items;
}
