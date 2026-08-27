package com.ysh.planning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ysh.planning.agent.domain.AgentAction;
import com.ysh.planning.agent.dto.AgentActionDto;
import com.ysh.planning.agent.policy.AgentActionPolicy;
import com.ysh.planning.agent.repository.AgentActionMapper;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
import com.ysh.planning.common.security.UserContext;
import com.ysh.planning.expense.dto.CreateExpenseRequest;
import com.ysh.planning.expense.dto.ExpenseDto;
import com.ysh.planning.expense.dto.UpdateExpenseRequest;
import com.ysh.planning.expense.service.ExpenseService;
import com.ysh.planning.plan.dto.CreateCategoryRequest;
import com.ysh.planning.plan.dto.SaveBudgetPlanRequest;
import com.ysh.planning.plan.service.CategoryService;
import com.ysh.planning.plan.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentActionService {
    private final AgentActionMapper actionMapper;
    private final ExpenseService expenseService;
    private final PlanService planService;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AgentActionDto createPending(String sessionId, String type, String summary, JsonNode payload) {
        Long userId = UserContext.currentUserId();
        AgentAction action = new AgentAction();
        action.setId(UUID.randomUUID().toString()); action.setSessionId(sessionId); action.setUserId(userId);
        action.setActionType(type); action.setSummary(summary.substring(0, Math.min(500, summary.length()))); action.setPayloadJson(payload.toString());
        action.setTargetFingerprint(fingerprintTarget(type, payload)); action.setIdempotencyKey(UUID.randomUUID().toString());
        action.setStatus("PENDING_CONFIRMATION"); action.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        action.setCreatedAt(LocalDateTime.now()); action.setUpdatedAt(LocalDateTime.now()); actionMapper.insert(action);
        return toDto(action);
    }

    public AgentActionDto confirm(String actionId) {
        Long userId = UserContext.currentUserId();
        try {
            return transactionTemplate.execute(status -> confirmInTransaction(actionId, userId));
        } catch (Exception e) {
            Throwable cause = e instanceof ActionExecutionException && e.getCause() != null ? e.getCause() : e;
            String message = cause instanceof BizException ? cause.getMessage() : "操作执行失败，请稍后重试";
            String result = objectMapper.createObjectNode().put("message", message).toString();
            actionMapper.failPending(actionId, userId, result);
            return toDto(requireOwned(actionId, userId));
        }
    }

    private AgentActionDto confirmInTransaction(String actionId, Long userId) {
        AgentAction action = requireOwned(actionId, userId); LocalDateTime now = LocalDateTime.now();
        if (!AgentActionPolicy.canConfirm(action.getStatus(), action.getExpiresAt(), now)) {
            if ("PENDING_CONFIRMATION".equals(action.getStatus()) && !action.getExpiresAt().isAfter(now)) actionMapper.expirePending(actionId, userId, now);
            return toDto(requireOwned(actionId, userId));
        }
        if (actionMapper.claim(actionId, userId) != 1) {
            AgentAction current = requireOwned(actionId, userId);
            if ("PENDING_CONFIRMATION".equals(current.getStatus()) && !current.getExpiresAt().isAfter(LocalDateTime.now())) actionMapper.expirePending(actionId, userId, LocalDateTime.now());
            return toDto(requireOwned(actionId, userId));
        }
        JsonNode payload = parse(action.getPayloadJson());
        String currentFingerprint;
        try { currentFingerprint = fingerprintTarget(action.getActionType(), payload, true); }
        catch (BizException e) { currentFingerprint = null; }
        if (action.getTargetFingerprint() != null && !action.getTargetFingerprint().equals(currentFingerprint)) {
            action.setStatus("STALE"); action.setUpdatedAt(now); actionMapper.updateById(action); return toDto(action);
        }
        try {
            Object result = execute(action.getActionType(), payload);
            action.setStatus("EXECUTED"); action.setResultJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) { throw new ActionExecutionException(e); }
        action.setUpdatedAt(LocalDateTime.now()); actionMapper.updateById(action); return toDto(action);
    }

    public AgentActionDto cancel(String actionId) {
        Long userId = UserContext.currentUserId(); AgentAction action = requireOwned(actionId, userId);
        if ("PENDING_CONFIRMATION".equals(action.getStatus())) {
            if (actionMapper.cancel(actionId, userId) != 1) actionMapper.expirePending(actionId, userId, LocalDateTime.now());
        }
        return toDto(requireOwned(actionId, userId));
    }

    public AgentActionDto getOwned(String actionId) {
        Long userId = UserContext.currentUserId(); AgentAction action = requireOwned(actionId, userId);
        if ("PENDING_CONFIRMATION".equals(action.getStatus()) && !action.getExpiresAt().isAfter(LocalDateTime.now())) {
            actionMapper.expirePending(action.getId(), userId, LocalDateTime.now());
            action = requireOwned(actionId, userId);
        }
        return toDto(action);
    }

    private Object execute(String type, JsonNode payload) throws Exception {
        return switch (type) {
            case "CREATE_EXPENSE" -> expenseService.create(objectMapper.treeToValue(payload, CreateExpenseRequest.class));
            case "UPDATE_EXPENSE" -> expenseService.update(requiredLong(payload, "expenseId"), objectMapper.treeToValue(without(payload, "expenseId"), UpdateExpenseRequest.class));
            case "DELETE_EXPENSE" -> { expenseService.delete(requiredLong(payload, "expenseId")); yield objectMapper.createObjectNode().put("deleted", true); }
            case "SAVE_PLAN" -> planService.savePlan(requiredText(payload, "yearMonth"), objectMapper.treeToValue(without(payload, "yearMonth"), SaveBudgetPlanRequest.class));
            case "CREATE_CATEGORY" -> categoryService.create(objectMapper.treeToValue(payload, CreateCategoryRequest.class));
            default -> throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不支持的动作类型");
        };
    }

    private String fingerprintTarget(String type, JsonNode payload) { return fingerprintTarget(type, payload, false); }
    private String fingerprintTarget(String type, JsonNode payload, boolean lock) {
        try {
            if ("UPDATE_EXPENSE".equals(type) || "DELETE_EXPENSE".equals(type)) {
                ExpenseDto target = lock ? expenseService.getByIdForUpdate(requiredLong(payload, "expenseId")) : expenseService.getById(requiredLong(payload, "expenseId"));
                return expenseFingerprint(target);
            }
            if ("SAVE_PLAN".equals(type)) {
                Object target = lock ? planService.getPlanForUpdate(requiredText(payload, "yearMonth")) : planService.getPlan(requiredText(payload, "yearMonth"));
                return sha256(objectMapper.writeValueAsString(target));
            }
            return null;
        } catch (BizException e) { throw e; } catch (Exception e) { throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "无法读取目标数据"); }
    }

    private AgentAction requireOwned(String id, Long userId) { AgentAction action = actionMapper.selectOne(new LambdaQueryWrapper<AgentAction>().eq(AgentAction::getId, id).eq(AgentAction::getUserId, userId)); if (action == null) throw new BizException(ErrorCode.NOT_FOUND); return action; }
    private JsonNode parse(String json) { try { return objectMapper.readTree(json); } catch (Exception e) { throw new BizException(ErrorCode.PARAM_ERROR); } }
    private long requiredLong(JsonNode node, String name) { if (!node.hasNonNull(name) || !node.get(name).canConvertToLong()) throw new BizException(ErrorCode.PARAM_ERROR); return node.get(name).asLong(); }
    private String requiredText(JsonNode node, String name) { if (!node.hasNonNull(name) || node.get(name).asText().isBlank()) throw new BizException(ErrorCode.PARAM_ERROR); return node.get(name).asText(); }
    private JsonNode without(JsonNode node, String field) { ObjectNode copy = node.deepCopy(); copy.remove(field); return copy; }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String expenseFingerprint(ExpenseDto expense) { return sha256(String.join("|", String.valueOf(expense.getExpenseId()), String.valueOf(expense.getCategoryId()), String.valueOf(expense.getAmount()), String.valueOf(expense.getExpenseDate()), String.valueOf(expense.getNote()), String.valueOf(expense.getUpdatedAt()))); }
    private AgentActionDto toDto(AgentAction action) { AgentActionDto dto = new AgentActionDto(); dto.setActionId(action.getId()); dto.setType(action.getActionType()); dto.setSummary(action.getSummary()); dto.setPayload(parse(action.getPayloadJson())); if (action.getResultJson() != null && !action.getResultJson().isBlank()) dto.setResult(parse(action.getResultJson())); dto.setStatus(action.getStatus()); dto.setExpiresAt(action.getExpiresAt()); return dto; }
    private static final class ActionExecutionException extends RuntimeException { private ActionExecutionException(Throwable cause) { super(cause); } }
}
