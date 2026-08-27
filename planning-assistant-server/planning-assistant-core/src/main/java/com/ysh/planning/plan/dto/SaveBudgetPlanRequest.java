package com.ysh.planning.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SaveBudgetPlanRequest {

    @NotEmpty(message = "预算明细不能为空")
    @Valid
    private List<BudgetItemDto> items;
}
