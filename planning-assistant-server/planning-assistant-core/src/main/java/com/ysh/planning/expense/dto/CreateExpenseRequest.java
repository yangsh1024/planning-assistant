package com.ysh.planning.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateExpenseRequest {

    @NotNull(message = "科目ID不能为空")
    private Long categoryId;

    @NotBlank(message = "金额不能为空")
    private String amount;

    @NotNull(message = "消费日期不能为空")
    private LocalDate expenseDate;

    @Size(max = 100, message = "备注长度不能超过100")
    private String note;
}
