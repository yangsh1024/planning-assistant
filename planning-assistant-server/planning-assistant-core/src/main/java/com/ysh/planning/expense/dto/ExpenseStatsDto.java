package com.ysh.planning.expense.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExpenseStatsDto {

    private String yearMonth;
    private String grandTotal;
    private Integer totalCount;
    private String dailyAvg;
    private List<ExpenseStatItemDto> items;
}
