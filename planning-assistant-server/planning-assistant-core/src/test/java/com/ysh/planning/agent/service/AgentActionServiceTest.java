package com.ysh.planning.agent.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ysh.planning.agent.domain.AgentAction;
import com.ysh.planning.agent.dto.AgentActionDto;
import com.ysh.planning.agent.repository.AgentActionMapper;
import com.ysh.planning.expense.dto.ExpenseDto;
import com.ysh.planning.expense.service.ExpenseService;
import com.ysh.planning.plan.service.CategoryService;
import com.ysh.planning.plan.service.PlanService;
import com.ysh.planning.plan.dto.BudgetPlanDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentActionServiceTest {
    @Mock private AgentActionMapper actionMapper;
    @Mock private ExpenseService expenseService;
    @Mock private PlanService planService;
    @Mock private CategoryService categoryService;
    @Mock private TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AtomicReference<AgentAction> stored = new AtomicReference<>();
    private AgentActionService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
        service = new AgentActionService(actionMapper, expenseService, planService, categoryService, objectMapper, transactionTemplate);
        when(actionMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> stored.get());
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void marksActionStaleWhenExpenseChangedBeforeConfirmation() {
        stubPendingActionCreation();
        ExpenseDto original = expense("10.00", LocalDateTime.of(2026, 8, 1, 10, 0));
        ExpenseDto changed = expense("20.00", LocalDateTime.of(2026, 8, 1, 11, 0));
        when(expenseService.getById(11L)).thenReturn(original);
        when(expenseService.getByIdForUpdate(11L)).thenReturn(changed);
        when(actionMapper.claim(anyString(), eq(7L))).thenReturn(1);
        ObjectNode payload = objectMapper.createObjectNode().put("expenseId", 11).put("amount", "30.00");
        AgentActionDto pending = service.createPending("session", "UPDATE_EXPENSE", "编辑开支", payload);

        AgentActionDto confirmed = service.confirm(pending.getActionId());

        assertThat(confirmed.getStatus()).isEqualTo("STALE");
        verify(expenseService, never()).update(anyLong(), any());
    }

    @Test
    void recordsFailedAfterTransactionalExecutionError() {
        stubPendingActionCreation();
        when(actionMapper.claim(anyString(), eq(7L))).thenReturn(1);
        when(expenseService.create(any())).thenThrow(new IllegalStateException("database unavailable"));
        when(actionMapper.failPending(anyString(), eq(7L), anyString())).thenAnswer(invocation -> {
            stored.get().setStatus("FAILED");
            stored.get().setResultJson(invocation.getArgument(2));
            return 1;
        });
        ObjectNode payload = objectMapper.createObjectNode().put("categoryId", 2).put("amount", "10.00").put("expenseDate", "2026-08-01");
        AgentActionDto pending = service.createPending("session", "CREATE_EXPENSE", "新增开支", payload);

        AgentActionDto confirmed = service.confirm(pending.getActionId());

        assertThat(confirmed.getStatus()).isEqualTo("FAILED");
        assertThat(confirmed.getResult().path("message").asText()).isEqualTo("操作执行失败，请稍后重试");
        verify(actionMapper).failPending(eq(pending.getActionId()), eq(7L), contains("操作执行失败"));
    }

    @Test
    void marksExpiredPendingActionWhenHistoryIsLoaded() {
        AgentAction action = new AgentAction(); action.setId("expired"); action.setUserId(7L); action.setSessionId("session");
        action.setActionType("CREATE_CATEGORY"); action.setSummary("创建科目"); action.setPayloadJson("{\"name\":\"旅行\"}");
        action.setStatus("PENDING_CONFIRMATION"); action.setExpiresAt(LocalDateTime.now().minusSeconds(1)); stored.set(action);
        when(actionMapper.expirePending(eq("expired"), eq(7L), any(LocalDateTime.class))).thenAnswer(invocation -> { stored.get().setStatus("EXPIRED"); return 1; });

        AgentActionDto result = service.getOwned("expired");

        assertThat(result.getStatus()).isEqualTo("EXPIRED");
        verify(actionMapper).expirePending(eq("expired"), eq(7L), any(LocalDateTime.class));
    }

    @Test
    void repeatedConfirmationReturnsExistingResultWithoutExecutingAgain() {
        AgentAction action = action("executed", "EXECUTED"); action.setResultJson("{\"categoryId\":9}"); stored.set(action);
        stubTransaction();

        AgentActionDto result = service.confirm("executed");

        assertThat(result.getStatus()).isEqualTo("EXECUTED");
        verify(actionMapper, never()).claim(anyString(), anyLong());
        verify(categoryService, never()).create(any());
    }

    @Test
    void concurrentConfirmationThatLosesClaimDoesNotExecuteBusinessService() {
        AgentAction action = action("concurrent", "PENDING_CONFIRMATION"); stored.set(action);
        stubTransaction();
        when(actionMapper.claim("concurrent", 7L)).thenAnswer(invocation -> { stored.get().setStatus("EXECUTED"); stored.get().setResultJson("{\"categoryId\":9}"); return 0; });

        AgentActionDto result = service.confirm("concurrent");

        assertThat(result.getStatus()).isEqualTo("EXECUTED");
        verify(categoryService, never()).create(any());
    }

    @Test
    void savesBudgetOnceWhenLockedSnapshotStillMatches() {
        stubPendingActionCreation();
        BudgetPlanDto snapshot = new BudgetPlanDto(); snapshot.setYearMonth("2026-08"); snapshot.setTotalBudget("100.00"); snapshot.setItems(List.of());
        when(planService.getPlan("2026-08")).thenReturn(snapshot);
        when(planService.getPlanForUpdate("2026-08")).thenReturn(snapshot);
        when(actionMapper.claim(anyString(), eq(7L))).thenReturn(1);
        when(planService.savePlan(eq("2026-08"), any())).thenReturn(snapshot);
        ObjectNode payload = objectMapper.createObjectNode().put("yearMonth", "2026-08");
        payload.putArray("items").addObject().put("categoryId", 2).put("amount", "100.00").put("sortOrder", 0);
        AgentActionDto pending = service.createPending("session", "SAVE_PLAN", "保存预算", payload);

        AgentActionDto result = service.confirm(pending.getActionId());

        assertThat(result.getStatus()).isEqualTo("EXECUTED");
        verify(planService, times(1)).savePlan(eq("2026-08"), any());
    }

    private ExpenseDto expense(String amount, LocalDateTime updatedAt) {
        ExpenseDto dto = new ExpenseDto(); dto.setExpenseId(11L); dto.setCategoryId(2L); dto.setAmount(amount);
        dto.setExpenseDate(LocalDate.of(2026, 8, 1)); dto.setUpdatedAt(updatedAt); return dto;
    }

    private void stubPendingActionCreation() {
        when(actionMapper.insert(any(AgentAction.class))).thenAnswer(invocation -> { stored.set(invocation.getArgument(0)); return 1; });
        stubTransaction();
    }

    private void stubTransaction() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private AgentAction action(String id, String status) {
        AgentAction action = new AgentAction(); action.setId(id); action.setUserId(7L); action.setSessionId("session");
        action.setActionType("CREATE_CATEGORY"); action.setSummary("创建科目：旅行"); action.setPayloadJson("{\"name\":\"旅行\"}");
        action.setStatus(status); action.setExpiresAt(LocalDateTime.now().plusMinutes(5)); return action;
    }
}
