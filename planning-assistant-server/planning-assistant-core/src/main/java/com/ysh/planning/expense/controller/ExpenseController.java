package com.ysh.planning.expense.controller;

import com.ysh.planning.common.response.PageData;
import com.ysh.planning.common.response.Result;
import com.ysh.planning.expense.dto.CreateExpenseRequest;
import com.ysh.planning.expense.dto.ExpenseDto;
import com.ysh.planning.expense.dto.ExpenseStatsDto;
import com.ysh.planning.expense.dto.TrendItemDto;
import com.ysh.planning.expense.dto.UpdateExpenseRequest;
import com.ysh.planning.expense.service.ExpenseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/api/expense")
@RequiredArgsConstructor
@Validated
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public Result<PageData<ExpenseDto>> list(
            @RequestParam String yearMonth,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.ok(expenseService.listByMonth(yearMonth, categoryId, page, pageSize));
    }

    @PostMapping
    public Result<ExpenseDto> create(@Valid @RequestBody CreateExpenseRequest req) {
        return Result.ok(expenseService.create(req));
    }

    @GetMapping("/stats/{yearMonth}")
    public Result<ExpenseStatsDto> stats(@PathVariable String yearMonth) {
        return Result.ok(expenseService.statsByMonth(yearMonth));
    }

    @GetMapping("/trend")
    public Result<List<TrendItemDto>> trend(
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return Result.ok(expenseService.trend(months));
    }

    @PutMapping("/{expenseId}")
    public Result<ExpenseDto> update(@PathVariable Long expenseId,
                                      @Valid @RequestBody UpdateExpenseRequest req) {
        return Result.ok(expenseService.update(expenseId, req));
    }

    @DeleteMapping("/{expenseId}")
    public Result<Void> delete(@PathVariable Long expenseId) {
        expenseService.delete(expenseId);
        return Result.ok();
    }
}
