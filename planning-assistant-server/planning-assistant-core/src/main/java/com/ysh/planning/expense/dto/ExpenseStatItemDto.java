package com.ysh.planning.expense.dto;

import lombok.Data;

@Data
public class ExpenseStatItemDto {

    private Long categoryId;
    private String categoryName;
    private String total;
    private Integer count;
    private Double percentage;
}
