package com.ysh.planning.expense.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExpenseWithCategoryDto {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
