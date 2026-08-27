package com.ysh.planning.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysh.planning.agent.dto.AgentActionDto;
import com.ysh.planning.agent.service.AgentActionService;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.expense.dto.ExpenseDto;
import com.ysh.planning.expense.service.ExpenseService;
import com.ysh.planning.plan.service.CategoryService;
import com.ysh.planning.plan.service.PlanService;
import com.ysh.planning.plan.dto.CategoryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentToolExecutorTest {
    @Mock private PlanService planService;
    @Mock private ExpenseService expenseService;
    @Mock private AgentActionService actionService;
    @Mock private CategoryService categoryService;
    private AgentToolExecutor executor;

    @BeforeEach
    void setUp() { executor = new AgentToolExecutor(planService, expenseService, actionService, categoryService, new ObjectMapper().findAndRegisterModules()); }

    @Test
    void rejectsOutOfRangeReadParametersBeforeCallingBusinessService() {
        assertThatThrownBy(() -> executor.execute("session", "read_expenses", "{\"yearMonth\":\"2026-08\",\"pageSize\":1000}"))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(expenseService);
    }

    @Test
    void rejectsDuplicateBudgetItemsBeforeCreatingAction() {
        CategoryDto category = new CategoryDto(); category.setCategoryId(1L); category.setName("饮食");
        when(categoryService.listAvailable()).thenReturn(java.util.List.of(category));
        String arguments = "{\"yearMonth\":\"2026-08\",\"items\":[" +
                "{\"categoryId\":1,\"amount\":\"10.00\",\"sortOrder\":0}," +
                "{\"categoryId\":1,\"amount\":\"20.00\",\"sortOrder\":1}]}";

        assertThatThrownBy(() -> executor.execute("session", "prepare_save_plan", arguments))
                .isInstanceOf(BizException.class).hasMessageContaining("重复");
        verifyNoInteractions(actionService);
    }

    @Test
    void buildsReadableDeleteConfirmationFromOwnedExpenseSnapshot() {
        ExpenseDto expense = new ExpenseDto(); expense.setExpenseId(11L); expense.setCategoryId(2L); expense.setCategoryName("饮食");
        expense.setAmount("36.50"); expense.setExpenseDate(LocalDate.of(2026, 8, 27)); expense.setNote("午餐");
        when(expenseService.getById(11L)).thenReturn(expense);
        when(actionService.createPending(eq("session"), eq("DELETE_EXPENSE"), anyString(), any())).thenReturn(new AgentActionDto());

        executor.execute("session", "prepare_delete_expense", "{\"expenseId\":11}");

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(actionService).createPending(eq("session"), eq("DELETE_EXPENSE"), summary.capture(), any());
        assertThat(summary.getValue()).contains("删除开支", "饮食", "36.50", "2026-08-27", "午餐");
    }
}
