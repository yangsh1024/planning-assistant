package com.ysh.planning.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
import com.ysh.planning.common.security.UserContext;
import com.ysh.planning.common.validation.MoneyValidator;
import com.ysh.planning.expense.repository.ExpenseMapper;
import com.ysh.planning.expense.dto.ExpenseStatRawDto;
import com.ysh.planning.plan.domain.BudgetItem;
import com.ysh.planning.plan.domain.BudgetPlan;
import com.ysh.planning.plan.domain.Category;
import com.ysh.planning.plan.dto.BudgetItemDto;
import com.ysh.planning.plan.dto.BudgetItemWithNameDto;
import com.ysh.planning.plan.dto.BudgetPlanDto;
import com.ysh.planning.plan.dto.BudgetSummaryDto;
import com.ysh.planning.plan.dto.BudgetSummaryItemDto;
import com.ysh.planning.plan.dto.SaveBudgetPlanRequest;
import com.ysh.planning.plan.repository.BudgetItemMapper;
import com.ysh.planning.plan.repository.BudgetPlanMapper;
import com.ysh.planning.plan.repository.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final BudgetPlanMapper budgetPlanMapper;
    private final BudgetItemMapper budgetItemMapper;
    private final CategoryMapper categoryMapper;
    private final ExpenseMapper expenseMapper;

    @Transactional
    public BudgetPlanDto savePlan(String yearMonth, SaveBudgetPlanRequest req) {
        Long userId = UserContext.currentUserId();
        validateYearMonth(yearMonth);

        List<Category> available = categoryMapper.selectAvailableByUserId(userId);
        Set<Long> availableIds = available.stream().map(Category::getId).collect(Collectors.toSet());

        Set<Long> seen = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (BudgetItemDto item : req.getItems()) {
            if (!seen.add(item.getCategoryId())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "科目不允许重复");
            }
            MoneyValidator.parsePositiveAmount(item.getAmount());
            if (!availableIds.contains(item.getCategoryId())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "科目ID " + item.getCategoryId() + " 不可用");
            }
            if (item.getSortOrder() < 0 || !sortOrders.add(item.getSortOrder())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "科目排序不正确");
            }
        }

        BudgetPlan existing = budgetPlanMapper.selectByUserIdAndYearMonth(userId, yearMonth);
        if (existing != null) {
            budgetItemMapper.delete(new LambdaQueryWrapper<BudgetItem>().eq(BudgetItem::getPlanId, existing.getId()));
            budgetPlanMapper.deleteById(existing.getId());
        }

        BudgetPlan plan = new BudgetPlan();
        plan.setUserId(userId);
        plan.setYearMonth(yearMonth);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        budgetPlanMapper.insert(plan);

        Map<Long, String> nameMap = available.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        List<BudgetItem> items = new ArrayList<>();
        for (int index = 0; index < req.getItems().size(); index++) {
            BudgetItemDto dto = req.getItems().get(index);
            BudgetItem item = new BudgetItem();
            item.setPlanId(plan.getId());
            item.setCategoryId(dto.getCategoryId());
            item.setAmount(MoneyValidator.parsePositiveAmount(dto.getAmount()));
            item.setSortOrder(dto.getSortOrder());
            items.add(item);
        }
        items.forEach(budgetItemMapper::insert);

        return buildPlanDto(yearMonth, nameMap, items);
    }

    public boolean hasAnyPlan() {
        return budgetPlanMapper.countByUserId(UserContext.currentUserId()) > 0;
    }

    public BudgetPlanDto getPlan(String yearMonth) {
        Long userId = UserContext.currentUserId();
        validateYearMonth(yearMonth);

        BudgetPlan plan = budgetPlanMapper.selectByUserIdAndYearMonth(userId, yearMonth);
        if (plan == null) {
            return null;
        }

        List<BudgetItemWithNameDto> rawItems = budgetItemMapper.selectWithNameByPlanId(plan.getId());
        List<BudgetItemDto> itemDtos = rawItems.stream().map(r -> {
            BudgetItemDto dto = new BudgetItemDto();
            dto.setCategoryId(r.getCategoryId());
            dto.setCategoryName(r.getCategoryName());
            dto.setAmount(r.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
            dto.setSortOrder(r.getSortOrder());
            return dto;
        }).collect(Collectors.toList());

        BigDecimal total = rawItems.stream()
                .map(BudgetItemWithNameDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BudgetPlanDto dto = new BudgetPlanDto();
        dto.setYearMonth(yearMonth);
        dto.setTotalBudget(total.setScale(2, RoundingMode.HALF_UP).toPlainString());
        dto.setItems(itemDtos);
        return dto;
    }

    public BudgetSummaryDto getSummary(String yearMonth) {
        Long userId = UserContext.currentUserId();
        validateYearMonth(yearMonth);

        BudgetPlan plan = budgetPlanMapper.selectByUserIdAndYearMonth(userId, yearMonth);
        List<BudgetItemWithNameDto> budgetItems = plan == null
                ? List.of()
                : budgetItemMapper.selectWithNameByPlanId(plan.getId());
        Map<Long, BudgetItemWithNameDto> budgetMap = budgetItems.stream()
                .collect(Collectors.toMap(BudgetItemWithNameDto::getCategoryId, b -> b));

        List<ExpenseStatRawDto> stats = expenseMapper.statsByMonth(userId, yearMonth);
        Map<Long, ExpenseStatRawDto> statsMap = stats.stream()
                .collect(Collectors.toMap(ExpenseStatRawDto::getCategoryId, s -> s));

        List<BudgetSummaryItemDto> items = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;

        for (BudgetItemWithNameDto budgetItem : budgetItems) {
            Long catId = budgetItem.getCategoryId();
            ExpenseStatRawDto stat = statsMap.get(catId);

            BigDecimal budget = budgetItem.getAmount();
            BigDecimal actual = stat != null ? stat.getTotal() : BigDecimal.ZERO;
            BigDecimal remaining = budget.subtract(actual);
            String categoryName = budgetItem.getCategoryName();

            double rate = 0.0;
            if (budget.compareTo(BigDecimal.ZERO) > 0) {
                rate = actual.divide(budget, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            BudgetSummaryItemDto item = new BudgetSummaryItemDto();
            item.setCategoryId(catId);
            item.setCategoryName(categoryName);
            item.setBudget(budget.setScale(2, RoundingMode.HALF_UP).toPlainString());
            item.setActual(actual.setScale(2, RoundingMode.HALF_UP).toPlainString());
            item.setRemaining(remaining.setScale(2, RoundingMode.HALF_UP).toPlainString());
            item.setRate(Math.round(rate * 100.0) / 100.0);
            items.add(item);

            totalBudget = totalBudget.add(budget);
            totalActual = totalActual.add(actual);
        }

        for (ExpenseStatRawDto stat : stats) {
            if (budgetMap.containsKey(stat.getCategoryId())) {
                continue;
            }
            BigDecimal actual = stat.getTotal();
            BudgetSummaryItemDto item = new BudgetSummaryItemDto();
            item.setCategoryId(stat.getCategoryId());
            item.setCategoryName(stat.getCategoryName());
            item.setBudget("0.00");
            item.setActual(actual.setScale(2, RoundingMode.HALF_UP).toPlainString());
            item.setRemaining(actual.negate().setScale(2, RoundingMode.HALF_UP).toPlainString());
            item.setRate(0.0);
            items.add(item);
            totalActual = totalActual.add(actual);
        }

        BigDecimal totalRemaining = totalBudget.subtract(totalActual);
        double totalRate = 0.0;
        if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
            totalRate = totalActual.divide(totalBudget, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        BudgetSummaryDto dto = new BudgetSummaryDto();
        dto.setYearMonth(yearMonth);
        dto.setTotalBudget(totalBudget.setScale(2, RoundingMode.HALF_UP).toPlainString());
        dto.setTotalActual(totalActual.setScale(2, RoundingMode.HALF_UP).toPlainString());
        dto.setTotalRemaining(totalRemaining.setScale(2, RoundingMode.HALF_UP).toPlainString());
        dto.setRate(Math.round(totalRate * 100.0) / 100.0);
        dto.setItems(items);
        return dto;
    }

    private BudgetPlanDto buildPlanDto(String yearMonth, Map<Long, String> nameMap, List<BudgetItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        List<BudgetItemDto> itemDtos = new ArrayList<>();
        for (BudgetItem item : items) {
            BudgetItemDto dto = new BudgetItemDto();
            dto.setCategoryId(item.getCategoryId());
            dto.setCategoryName(nameMap.getOrDefault(item.getCategoryId(), ""));
            dto.setAmount(item.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
            dto.setSortOrder(item.getSortOrder());
            itemDtos.add(dto);
            total = total.add(item.getAmount());
        }

        BudgetPlanDto dto = new BudgetPlanDto();
        dto.setYearMonth(yearMonth);
        dto.setTotalBudget(total.setScale(2, RoundingMode.HALF_UP).toPlainString());
        dto.setItems(itemDtos);
        return dto;
    }

    private void validateYearMonth(String yearMonth) {
        if (!yearMonth.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "yearMonth 格式不正确，应为 yyyy-MM");
        }
        if (YearMonth.parse(yearMonth).isAfter(YearMonth.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不支持设置未来月份预算");
        }
    }
}
