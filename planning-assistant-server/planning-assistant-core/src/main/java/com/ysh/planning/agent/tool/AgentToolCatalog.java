package com.ysh.planning.agent.tool;

import java.util.List;
import java.util.Map;

public final class AgentToolCatalog {
    private AgentToolCatalog() {
    }

    public static List<Map<String, Object>> definitions() {
        return List.of(
                tool("read_budget_summary", "查询指定月份预算执行", props(Map.of("yearMonth", string("yyyy-MM"))), List.of("yearMonth")),
                tool("read_expenses", "分页查询指定月份开支", props(Map.of("yearMonth", string("yyyy-MM"), "categoryId", positiveInteger(), "page", boundedInteger(1, 10_000), "pageSize", boundedInteger(1, 100))), List.of("yearMonth")),
                tool("read_expense_stats", "查询指定月份科目汇总", props(Map.of("yearMonth", string("yyyy-MM"))), List.of("yearMonth")),
                tool("read_trend", "查询最近月份消费趋势", props(Map.of("months", boundedInteger(1, 24))), List.of()),
                tool("prepare_create_expense", "生成新增开支确认卡片", expenseProps(false), List.of("categoryId", "amount", "expenseDate")),
                tool("prepare_update_expense", "生成编辑开支确认卡片", expenseProps(true), List.of("expenseId")),
                tool("prepare_delete_expense", "生成删除开支确认卡片", props(Map.of("expenseId", integer())), List.of("expenseId")),
                tool("prepare_save_plan", "生成保存整月预算确认卡片", props(Map.of("yearMonth", string("yyyy-MM"), "items", budgetItems())), List.of("yearMonth", "items")),
                tool("prepare_create_category", "生成创建科目确认卡片", props(Map.of("name", string("科目名称"))), List.of("name"))
        );
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> parameters, List<String> required) {
        parameters.put("required", required);
        parameters.put("additionalProperties", false);
        return Map.of("type", "function", "name", name, "description", description, "parameters", parameters);
    }

    private static Map<String, Object> expenseProps(boolean update) {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        if (update) fields.put("expenseId", integer());
        fields.put("categoryId", integer());
        fields.put("amount", string("两位小数字符串"));
        fields.put("expenseDate", string("yyyy-MM-dd"));
        fields.put("note", string("备注"));
        return props(fields);
    }

    private static Map<String, Object> budgetItems() {
        Map<String, Object> item = props(Map.of("categoryId", positiveInteger(), "amount", string("两位小数字符串"), "sortOrder", boundedInteger(0, 10_000)));
        item.put("required", List.of("categoryId", "amount", "sortOrder"));
        item.put("additionalProperties", false);
        return Map.of("type", "array", "minItems", 1, "items", item);
    }

    private static Map<String, Object> props(Map<String, Object> fields) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("type", "object");
        result.put("properties", fields);
        return result;
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> integer() {
        return Map.of("type", "integer");
    }

    private static Map<String, Object> positiveInteger() {
        return Map.of("type", "integer", "minimum", 1);
    }

    private static Map<String, Object> boundedInteger(int min, int max) {
        return Map.of("type", "integer", "minimum", min, "maximum", max);
    }
}
