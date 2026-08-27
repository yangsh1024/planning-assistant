package com.ysh.planning.plan.service;

import com.ysh.planning.expense.dto.ExpenseStatRawDto;
import com.ysh.planning.expense.repository.ExpenseMapper;
import com.ysh.planning.plan.dto.BudgetSummaryDto;
import com.ysh.planning.plan.repository.BudgetItemMapper;
import com.ysh.planning.plan.repository.BudgetPlanMapper;
import com.ysh.planning.plan.repository.CategoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private BudgetPlanMapper budgetPlanMapper;
    @Mock
    private BudgetItemMapper budgetItemMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ExpenseMapper expenseMapper;

    private PlanService planService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
        planService = new PlanService(budgetPlanMapper, budgetItemMapper, categoryMapper, expenseMapper);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void summaryKeepsActualExpensesWhenMonthHasNoPlan() {
        ExpenseStatRawDto stat = new ExpenseStatRawDto();
        stat.setCategoryId(12L);
        stat.setCategoryName("饮食");
        stat.setTotal(new BigDecimal("38.50"));
        stat.setCount(1);
        when(budgetPlanMapper.selectByUserIdAndYearMonth(7L, "2026-08")).thenReturn(null);
        when(expenseMapper.statsByMonth(7L, "2026-08")).thenReturn(List.of(stat));

        BudgetSummaryDto summary = planService.getSummary("2026-08");

        assertEquals("0.00", summary.getTotalBudget());
        assertEquals("38.50", summary.getTotalActual());
        assertEquals("-38.50", summary.getTotalRemaining());
        assertEquals(1, summary.getItems().size());
        assertEquals("0.00", summary.getItems().getFirst().getBudget());
        assertEquals("-38.50", summary.getItems().getFirst().getRemaining());
    }

    @Test
    void hasAnyPlanUsesCurrentUserOnly() {
        when(budgetPlanMapper.countByUserId(7L)).thenReturn(0L);

        assertFalse(planService.hasAnyPlan());
    }
}
