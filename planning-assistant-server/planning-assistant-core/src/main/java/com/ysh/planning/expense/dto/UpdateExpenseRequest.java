package com.ysh.planning.expense.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateExpenseRequest {

    private Long categoryId;
    private String amount;
    private LocalDate expenseDate;

    @Size(max = 100, message = "备注长度不能超过100")
    private String note;
}
