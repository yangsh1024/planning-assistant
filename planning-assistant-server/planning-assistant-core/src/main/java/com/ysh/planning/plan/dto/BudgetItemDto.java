package com.ysh.planning.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BudgetItemDto {

    @NotNull(message = "科目ID不能为空")
    private Long categoryId;

    private String categoryName;

    @NotBlank(message = "预算金额不能为空")
    private String amount;

    @NotNull(message = "科目排序不能为空")
    private Integer sortOrder;
}
