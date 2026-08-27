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

@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @PostMapping("/{yearMonth}")
    public Result<BudgetPlanDto> savePlan(@PathVariable String yearMonth,
                                          @Valid @RequestBody SaveBudgetPlanRequest req) {
        return Result.ok(planService.savePlan(yearMonth, req));
    }

    @GetMapping("/{yearMonth}")
    public Result<BudgetPlanDto> getPlan(@PathVariable String yearMonth) {
        return Result.ok(planService.getPlan(yearMonth));
    }

    @GetMapping("/has-any")
    public Result<Boolean> hasAnyPlan() {
        return Result.ok(planService.hasAnyPlan());
    }

    @GetMapping("/{yearMonth}/summary")
    public Result<BudgetSummaryDto> getSummary(@PathVariable String yearMonth) {
        return Result.ok(planService.getSummary(yearMonth));
    }
}
