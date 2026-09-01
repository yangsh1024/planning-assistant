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

/** 暴露当前用户的开支录入、查询、统计和趋势接口。 */
@RestController
@RequestMapping("/api/expense")
@RequiredArgsConstructor
@Validated
public class ExpenseController {

    private final ExpenseService expenseService;

    /** 查询指定月份的开支，并支持分类筛选和分页。 */
    @GetMapping
    public Result<PageData<ExpenseDto>> list(
            @RequestParam String yearMonth,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.ok(expenseService.listByMonth(yearMonth, categoryId, page, pageSize));
    }

    /** 新增当前用户的一笔开支。 */
    @PostMapping
    public Result<ExpenseDto> create(@Valid @RequestBody CreateExpenseRequest req) {
        return Result.ok(expenseService.create(req));
    }

    /** 汇总指定月份按分类计算的开支统计。 */
    @GetMapping("/stats/{yearMonth}")
    public Result<ExpenseStatsDto> stats(@PathVariable String yearMonth) {
        return Result.ok(expenseService.statsByMonth(yearMonth));
    }

    /** 查询最近若干个月的开支趋势。 */
    @GetMapping("/trend")
    public Result<List<TrendItemDto>> trend(
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return Result.ok(expenseService.trend(months));
    }

    /** 更新当前用户拥有的开支记录。 */
    @PutMapping("/{expenseId}")
    public Result<ExpenseDto> update(@PathVariable Long expenseId,
                                      @Valid @RequestBody UpdateExpenseRequest req) {
        return Result.ok(expenseService.update(expenseId, req));
    }

    /** 软删除当前用户拥有的开支记录。 */
    @DeleteMapping("/{expenseId}")
    public Result<Void> delete(@PathVariable Long expenseId) {
        expenseService.delete(expenseId);
        return Result.ok();
    }
}
