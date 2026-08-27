package com.ysh.planning.expense.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExpenseDto {

    private Long expenseId;
    private Long categoryId;
    private String categoryName;
    private String amount;
    private LocalDate expenseDate;
    private String note;
    private LocalDateTime createdAt;
    @JsonIgnore
    private LocalDateTime updatedAt;
}
