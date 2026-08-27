package com.ysh.planning.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
import com.ysh.planning.common.response.PageData;
import com.ysh.planning.common.security.UserContext;
import com.ysh.planning.common.validation.MoneyValidator;
import com.ysh.planning.expense.domain.Expense;
import com.ysh.planning.expense.dto.CreateExpenseRequest;
import com.ysh.planning.expense.dto.ExpenseDto;
import com.ysh.planning.expense.dto.ExpenseStatItemDto;
import com.ysh.planning.expense.dto.ExpenseStatRawDto;
import com.ysh.planning.expense.dto.ExpenseStatsDto;
import com.ysh.planning.expense.dto.ExpenseWithCategoryDto;
import com.ysh.planning.expense.dto.TrendItemDto;
import com.ysh.planning.expense.dto.TrendRawDto;
import com.ysh.planning.expense.dto.UpdateExpenseRequest;
import com.ysh.planning.expense.repository.ExpenseMapper;
import com.ysh.planning.plan.domain.Category;
import com.ysh.planning.plan.repository.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseMapper expenseMapper;
    private final CategoryMapper categoryMapper;

    public ExpenseDto create(CreateExpenseRequest req) {
        Long userId = UserContext.currentUserId();
        BigDecimal amount = MoneyValidator.parsePositiveAmount(req.getAmount());
        validateExpenseDate(req.getExpenseDate());

        validateCategoryAvailable(userId, req.getCategoryId());

        Expense expense = new Expense();
        expense.setUserId(userId);
        expense.setCategoryId(req.getCategoryId());
        expense.setAmount(amount);
        expense.setExpenseDate(req.getExpenseDate());
        expense.setNote(req.getNote());
        expense.setIsDeleted(false);
        expense.setCreatedAt(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        expenseMapper.insert(expense);

        ExpenseWithCategoryDto full = expenseMapper.selectWithCategoryById(expense.getId());
        return toDto(full);
    }

    public ExpenseDto update(Long expenseId, UpdateExpenseRequest req) {
        Long userId = UserContext.currentUserId();
        Expense expense = expenseMapper.selectOne(
                new LambdaQueryWrapper<Expense>()
                        .eq(Expense::getId, expenseId)
                        .eq(Expense::getUserId, userId)
                        .eq(Expense::getIsDeleted, false));
        if (expense == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "记录不存在或不属于当前用户");
        }

        if (req.getCategoryId() != null) {
            validateCategoryAvailable(userId, req.getCategoryId());
            expense.setCategoryId(req.getCategoryId());
        }
        if (req.getAmount() != null) {
            expense.setAmount(MoneyValidator.parsePositiveAmount(req.getAmount()));
        }
        if (req.getExpenseDate() != null) {
            validateExpenseDate(req.getExpenseDate());
            expense.setExpenseDate(req.getExpenseDate());
        }
        if (req.getNote() != null) {
            expense.setNote(req.getNote());
        }
        expense.setUpdatedAt(LocalDateTime.now());
        expenseMapper.updateById(expense);

        ExpenseWithCategoryDto full = expenseMapper.selectWithCategoryById(expenseId);
        return toDto(full);
    }

    public void delete(Long expenseId) {
        Long userId = UserContext.currentUserId();
        Expense expense = expenseMapper.selectOne(
                new LambdaQueryWrapper<Expense>()
                        .eq(Expense::getId, expenseId)
                        .eq(Expense::getUserId, userId)
                        .eq(Expense::getIsDeleted, false));
        if (expense == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "记录不存在或不属于当前用户");
        }
        expense.setIsDeleted(true);
        expense.setUpdatedAt(LocalDateTime.now());
        expenseMapper.updateById(expense);
    }

    public PageData<ExpenseDto> listByMonth(String yearMonth, Long categoryId, int page, int pageSize) {
        Long userId = UserContext.currentUserId();
        validateYearMonth(yearMonth);
        long offset = (long) (page - 1) * pageSize;
        List<ExpenseWithCategoryDto> raw = expenseMapper.listByMonth(userId, yearMonth, categoryId, pageSize, offset);
        long total = expenseMapper.countByMonth(userId, yearMonth, categoryId);

        List<ExpenseDto> list = raw.stream().map(this::toDto).collect(Collectors.toList());
        return new PageData<>(total, page, pageSize, list);
    }

    public ExpenseStatsDto statsByMonth(String yearMonth) {
        Long userId = UserContext.currentUserId();
        validateYearMonth(yearMonth);
        List<ExpenseStatRawDto> raw = expenseMapper.statsByMonth(userId, yearMonth);

        BigDecimal grandTotal = raw.stream()
                .map(ExpenseStatRawDto::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ExpenseStatItemDto> items = raw.stream().map(r -> {
            double percentage = 0.0;
            if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                percentage = r.getTotal().divide(grandTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
                percentage = Math.round(percentage * 100.0) / 100.0;
            }
            ExpenseStatItemDto dto = new ExpenseStatItemDto();
            dto.setCategoryId(r.getCategoryId());
            dto.setCategoryName(r.getCategoryName());
            dto.setTotal(r.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString());
            dto.setCount(r.getCount());
            dto.setPercentage(percentage);
            return dto;
        }).collect(Collectors.toList());

        ExpenseStatsDto dto = new ExpenseStatsDto();
        dto.setYearMonth(yearMonth);
        dto.setGrandTotal(grandTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
        dto.setTotalCount(raw.stream().mapToInt(ExpenseStatRawDto::getCount).sum());
        int days = YearMonth.parse(yearMonth).lengthOfMonth();
        BigDecimal dailyAvg = grandTotal.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        dto.setDailyAvg(dailyAvg.toPlainString());
        dto.setItems(items);
        return dto;
    }

    public List<TrendItemDto> trend(int months) {
        Long userId = UserContext.currentUserId();
        YearMonth current = YearMonth.now();
        List<String> monthList = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            monthList.add(current.minusMonths(i).toString());
        }

        List<TrendRawDto> raw = expenseMapper.trendByMonths(userId, monthList);
        Map<String, BigDecimal> rawMap = raw.stream()
                .collect(Collectors.toMap(TrendRawDto::getYearMonth, TrendRawDto::getTotal));

        return monthList.stream().map(m -> {
            TrendItemDto dto = new TrendItemDto();
            dto.setYearMonth(m);
            BigDecimal total = rawMap.getOrDefault(m, BigDecimal.ZERO);
            dto.setTotal(total.setScale(2, RoundingMode.HALF_UP).toPlainString());
            return dto;
        }).collect(Collectors.toList());
    }

    private void validateCategoryAvailable(Long userId, Long categoryId) {
        List<Category> available = categoryMapper.selectAvailableByUserId(userId);
        boolean valid = available.stream().anyMatch(c -> c.getId().equals(categoryId));
        if (!valid) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "科目不可用或不存在");
        }
    }

    private void validateExpenseDate(LocalDate expenseDate) {
        if (expenseDate == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "消费日期不能为空");
        }
        if (expenseDate.isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不允许记录未来日期的开销");
        }
    }

    private void validateYearMonth(String yearMonth) {
        if (yearMonth == null || !yearMonth.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "yearMonth 格式不正确，应为 yyyy-MM");
        }
        if (YearMonth.parse(yearMonth).isAfter(YearMonth.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不支持查询未来月份开销");
        }
    }

    private ExpenseDto toDto(ExpenseWithCategoryDto raw) {
        if (raw == null) return null;
        ExpenseDto dto = new ExpenseDto();
        dto.setExpenseId(raw.getId());
        dto.setCategoryId(raw.getCategoryId());
        dto.setCategoryName(raw.getCategoryName());
        dto.setAmount(raw.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        dto.setExpenseDate(raw.getExpenseDate());
        dto.setNote(raw.getNote());
        dto.setCreatedAt(raw.getCreatedAt());
        return dto;
    }
}
