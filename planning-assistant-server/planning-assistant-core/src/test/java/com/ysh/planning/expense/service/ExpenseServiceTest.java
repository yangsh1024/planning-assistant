package com.ysh.planning.expense.service;

import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.expense.repository.ExpenseMapper;
import com.ysh.planning.plan.repository.CategoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private CategoryMapper categoryMapper;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
        expenseService = new ExpenseService(expenseMapper, categoryMapper);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void statsRejectsFutureMonth() {
        String nextMonth = YearMonth.now().plusMonths(1).toString();

        assertThrows(BizException.class, () -> expenseService.statsByMonth(nextMonth));
    }
}
