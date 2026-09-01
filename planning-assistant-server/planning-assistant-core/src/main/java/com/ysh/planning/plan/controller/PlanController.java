package com.ysh.planning.plan.controller;

import com.ysh.planning.common.response.Result;
import com.ysh.planning.plan.dto.BudgetPlanDto;
import com.ysh.planning.plan.dto.BudgetSummaryDto;
import com.ysh.planning.plan.dto.SaveBudgetPlanRequest;
import com.ysh.planning.plan.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供月度预算方案的保存、读取和执行汇总接口。 */
@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    /** 保存指定月份的完整预算方案。 */
    @PostMapping("/{yearMonth}")
    public Result<BudgetPlanDto> savePlan(@PathVariable String yearMonth,
                                          @Valid @RequestBody SaveBudgetPlanRequest req) {
        return Result.ok(planService.savePlan(yearMonth, req));
    }

    /** 查询指定月份的预算方案。 */
    @GetMapping("/{yearMonth}")
    public Result<BudgetPlanDto> getPlan(@PathVariable String yearMonth) {
        return Result.ok(planService.getPlan(yearMonth));
    }

    /** 判断当前用户是否至少保存过一份预算方案。 */
    @GetMapping("/has-any")
    public Result<Boolean> hasAnyPlan() {
        return Result.ok(planService.hasAnyPlan());
    }

    /** 汇总指定月份的预算额度和实际支出。 */
    @GetMapping("/{yearMonth}/summary")
    public Result<BudgetSummaryDto> getSummary(@PathVariable String yearMonth) {
        return Result.ok(planService.getSummary(yearMonth));
    }
}
