package com.ysh.planning.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysh.planning.agent.dto.AgentActionDto;
import com.ysh.planning.agent.service.AgentActionService;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
import com.ysh.planning.expense.service.ExpenseService;
import com.ysh.planning.expense.dto.ExpenseDto;
import com.ysh.planning.plan.service.PlanService;
import com.ysh.planning.plan.service.CategoryService;
import com.ysh.planning.plan.dto.CategoryDto;
import com.ysh.planning.common.validation.MoneyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 执行模型允许调用的账本工具，并把写入请求转换为确认卡片。
 * 所有工具参数先由服务端复核，模型输出不能直接越过现有业务校验。
 */
@Component
@RequiredArgsConstructor
public class AgentToolExecutor {
    private final PlanService planService;
    private final ExpenseService expenseService;
    private final AgentActionService actionService;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;

    /**
     * 执行一次模型工具调用。
     * <ol><li>解析参数</li><li>校验边界</li><li>返回结果</li></ol>
     *
     * @param sessionId 当前会话标识
     * @param name      已登记的工具名称
     * @param arguments 模型返回的 JSON 参数
     * @return 查询结果或待确认操作
     * @throws BizException 工具或参数不受支持时抛出
     */
    public ToolResult execute(String sessionId, String name, String arguments) {
        try {
            JsonNode args = objectMapper.readTree(arguments);
            // 限制字段集合，避免模型传入未被业务契约接受的额外数据。
            validateShape(name, args);
            Object result = switch (name) {
                case "read_budget_summary" -> planService.getSummary(text(args, "yearMonth"));
                case "read_expenses" ->
                        expenseService.listByMonth(text(args, "yearMonth"), nullableLong(args, "categoryId"), boundedInteger(args, "page", 1, 1, 10_000), boundedInteger(args, "pageSize", 20, 1, 100));
                case "read_expense_stats" -> expenseService.statsByMonth(text(args, "yearMonth"));
                case "read_trend" -> expenseService.trend(boundedInteger(args, "months", 6, 1, 24));
                case "prepare_create_expense" -> pending(sessionId, "CREATE_EXPENSE", "新增一笔开支", args);
                case "prepare_update_expense" -> pending(sessionId, "UPDATE_EXPENSE", "编辑开支记录", args);
                case "prepare_delete_expense" -> pending(sessionId, "DELETE_EXPENSE", "删除开支记录", args);
                case "prepare_save_plan" -> pending(sessionId, "SAVE_PLAN", "保存月度预算", args);
                case "prepare_create_category" -> pending(sessionId, "CREATE_CATEGORY", "创建科目", args);
                default -> throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不支持的工具");
            };
            // 写操作只返回确认卡片，不能在模型工具调用阶段落库。
            if (result instanceof AgentActionDto action) return new ToolResult("等待用户确认", action);
            return new ToolResult(objectMapper.writeValueAsString(result), null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "工具参数不合法");
        }
    }

    /**
     * 验证工具参数只包含声明过的字段。
     * <ol><li>校验对象</li><li>限制字段</li><li>校验明细</li></ol>
     *
     * @param name 工具名称
     * @param args 模型提供的参数对象
     * @throws BizException 参数结构或字段不符合工具契约时抛出
     */
    private void validateShape(String name, JsonNode args) {
        if (args == null || !args.isObject())
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "工具参数必须是对象");
        java.util.Set<String> allowed = switch (name) {
            case "read_budget_summary", "read_expense_stats" -> java.util.Set.of("yearMonth");
            case "read_expenses" -> java.util.Set.of("yearMonth", "categoryId", "page", "pageSize");
            case "read_trend" -> java.util.Set.of("months");
            case "prepare_create_expense" -> java.util.Set.of("categoryId", "amount", "expenseDate", "note");
            case "prepare_update_expense" ->
                    java.util.Set.of("expenseId", "categoryId", "amount", "expenseDate", "note");
            case "prepare_delete_expense" -> java.util.Set.of("expenseId");
            case "prepare_save_plan" -> java.util.Set.of("yearMonth", "items");
            case "prepare_create_category" -> java.util.Set.of("name");
            default -> throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不支持的工具");
        };
        args.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field))
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "工具参数包含未知字段：" + field);
        });
        if ("prepare_save_plan".equals(name) && args.path("items").isArray()) {
            args.path("items").forEach(item -> {
                if (!item.isObject()) throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "预算明细格式不正确");
                item.fieldNames().forEachRemaining(field -> {
                    if (!java.util.Set.of("categoryId", "amount", "sortOrder").contains(field))
                        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "预算明细包含未知字段：" + field);
                });
            });
        }
    }

    /**
     * 生成已通过常规业务校验的确认卡片。
     * <ol><li>校验意图</li><li>生成摘要</li><li>保存确认</li></ol>
     *
     * @param sessionId 当前会话标识
     * @param type 写操作类型
     * @param summary 模型提供的回退摘要
     * @param args 已解析的工具参数
     * @return 待用户确认的动作
     * @throws BizException 数据不符合既有账本规则时抛出
     */
    // 复用业务规则校验，确认卡片必须与常规入口接受同一份合法数据。
    private AgentActionDto pending(String sessionId, String type, String summary, JsonNode args) {
        validatePending(type, args);
        return actionService.createPending(sessionId, type, readableSummary(type, summary, args), args);
    }

    private void validatePending(String type, JsonNode args) {
        if ("CREATE_EXPENSE".equals(type) || "UPDATE_EXPENSE".equals(type)) {
            if ("UPDATE_EXPENSE".equals(type)) {
                positiveLong(args, "expenseId");
                expenseService.getById(args.get("expenseId").asLong());
                if (!args.hasNonNull("categoryId") && !args.hasNonNull("amount") && !args.hasNonNull("expenseDate") && !args.hasNonNull("note"))
                    throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "编辑内容不能为空");
            }
            if ("CREATE_EXPENSE".equals(type) || args.hasNonNull("amount"))
                MoneyValidator.parsePositiveAmount(text(args, "amount"));
            if ("CREATE_EXPENSE".equals(type) || args.hasNonNull("expenseDate")) {
                java.time.LocalDate date;
                try {
                    date = java.time.LocalDate.parse(text(args, "expenseDate"));
                } catch (Exception e) {
                    throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "日期格式不正确");
                }
                if (date.isAfter(java.time.LocalDate.now()))
                    throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不允许记录未来开支");
            }
            if ("CREATE_EXPENSE".equals(type) || args.hasNonNull("categoryId")) {
                long categoryId = args.path("categoryId").asLong(-1);
                boolean available = categoryService.listAvailable().stream().anyMatch(c -> c.getCategoryId() == categoryId);
                if (!available) throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "科目不可用");
            }
            if (args.hasNonNull("note") && args.get("note").asText().length() > 100)
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "备注过长");
        } else if ("SAVE_PLAN".equals(type)) {
            text(args, "yearMonth");
            if (!args.has("items") || !args.get("items").isArray() || args.get("items").isEmpty())
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "预算明细不能为空");
            java.util.Set<Long> categoryIds = new java.util.HashSet<>();
            java.util.Set<Integer> sortOrders = new java.util.HashSet<>();
            java.util.Set<Long> availableIds = categoryService.listAvailable().stream().map(CategoryDto::getCategoryId).collect(java.util.stream.Collectors.toSet());
            args.get("items").forEach(item -> {
                long categoryId = positiveLong(item, "categoryId");
                if (!categoryIds.add(categoryId))
                    throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "预算科目不能重复");
                if (!availableIds.contains(categoryId))
                    throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "科目不可用");
                MoneyValidator.parsePositiveAmount(text(item, "amount"));
                int sortOrder = boundedInteger(item, "sortOrder", -1, 0, 10_000);
                if (!sortOrders.add(sortOrder))
                    throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "预算排序不能重复");
            });
        } else if ("CREATE_CATEGORY".equals(type)) {
            String name = text(args, "name");
            if (name.length() > 20) throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "科目名称过长");
        } else if ("DELETE_EXPENSE".equals(type)) {
            expenseService.getById(positiveLong(args, "expenseId"));
        }
    }

    private String readableSummary(String type, String fallback, JsonNode args) {
        return switch (type) {
            case "CREATE_EXPENSE" ->
                    "新增开支：" + categoryName(args.get("categoryId").asLong()) + " " + text(args, "amount") + " 元，" + text(args, "expenseDate") + note(args);
            case "UPDATE_EXPENSE" -> updateSummary(args);
            case "DELETE_EXPENSE" ->
                    "删除开支：" + expenseSummary(expenseService.getById(args.get("expenseId").asLong()));
            case "SAVE_PLAN" -> budgetSummary(args);
            case "CREATE_CATEGORY" -> "创建科目：" + text(args, "name");
            default -> fallback;
        };
    }

    private String updateSummary(JsonNode args) {
        ExpenseDto before = expenseService.getById(args.get("expenseId").asLong());
        String category = args.hasNonNull("categoryId") ? categoryName(args.get("categoryId").asLong()) : before.getCategoryName();
        String amount = args.hasNonNull("amount") ? args.get("amount").asText() : before.getAmount();
        String date = args.hasNonNull("expenseDate") ? args.get("expenseDate").asText() : before.getExpenseDate().toString();
        String note = args.hasNonNull("note") ? args.get("note").asText() : before.getNote();
        return "编辑开支：原「" + expenseSummary(before) + "」；改为「" + category + " " + amount + " 元，" + date + optionalNote(note) + "」";
    }

    private String budgetSummary(JsonNode args) {
        java.util.Map<Long, String> names = categoryService.listAvailable().stream().collect(java.util.stream.Collectors.toMap(CategoryDto::getCategoryId, CategoryDto::getName));
        java.util.StringJoiner items = new java.util.StringJoiner("；");
        args.get("items").forEach(item -> items.add(names.getOrDefault(item.get("categoryId").asLong(), "科目#" + item.get("categoryId").asLong()) + " " + item.get("amount").asText() + " 元"));
        return "保存 " + text(args, "yearMonth") + " 预算：" + items;
    }

    private String expenseSummary(ExpenseDto expense) {
        return expense.getCategoryName() + " " + expense.getAmount() + " 元，" + expense.getExpenseDate() + optionalNote(expense.getNote());
    }

    private String categoryName(long categoryId) {
        return categoryService.listAvailable().stream().filter(item -> item.getCategoryId() == categoryId).map(CategoryDto::getName).findFirst().orElse("科目#" + categoryId);
    }

    private String note(JsonNode args) {
        return args.hasNonNull("note") ? optionalNote(args.get("note").asText()) : "";
    }

    private String optionalNote(String note) {
        return note == null || note.isBlank() ? "" : "，备注：" + note;
    }

    private String text(JsonNode args, String field) {
        if (!args.hasNonNull(field) || args.get(field).asText().isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR);
        return args.get(field).asText();
    }

    private Long nullableLong(JsonNode args, String field) {
        return args.hasNonNull(field) ? positiveLong(args, field) : null;
    }

    private long positiveLong(JsonNode args, String field) {
        if (!args.hasNonNull(field) || !args.get(field).canConvertToLong() || args.get(field).asLong() <= 0)
            throw new BizException(ErrorCode.PARAM_ERROR);
        return args.get(field).asLong();
    }

    private int boundedInteger(JsonNode args, String field, int fallback, int min, int max) {
        int value = args.hasNonNull(field) && args.get(field).canConvertToInt() ? args.get(field).asInt() : fallback;
        if (value < min || value > max) throw new BizException(ErrorCode.PARAM_ERROR);
        return value;
    }

    public record ToolResult(String output, AgentActionDto action) {
    }
}
