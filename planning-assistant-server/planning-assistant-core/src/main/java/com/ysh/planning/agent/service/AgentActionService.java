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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 管理 Agent 写操作从确认卡片到最终执行的状态流转。
 * 在执行前核验目标快照，避免用户确认旧数据后覆盖账本的新变化。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentActionService {
    private final AgentActionMapper actionMapper;
    private final ExpenseService expenseService;
    private final PlanService planService;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建等待用户确认的写操作。
     * <ol><li>保存意图</li><li>记录快照</li><li>设定时限</li></ol>
     * @param sessionId 发起操作的会话标识
     * @param type 受支持的写操作类型
     * @param summary 展示给用户的操作摘要
     * @param payload 经工具校验后的操作内容
     * @return 待确认操作的展示数据
     * @throws BizException 目标数据无法读取时抛出
     */
    public AgentActionDto createPending(String sessionId, String type, String summary, JsonNode payload) {
        Long userId = UserContext.currentUserId();
        AgentAction action = new AgentAction();
        action.setId(UUID.randomUUID().toString()); action.setSessionId(sessionId); action.setUserId(userId);
        action.setActionType(type); action.setSummary(summary.substring(0, Math.min(500, summary.length()))); action.setPayloadJson(payload.toString());
        action.setTargetFingerprint(fingerprintTarget(type, payload)); action.setIdempotencyKey(UUID.randomUUID().toString());
        action.setStatus("PENDING_CONFIRMATION"); action.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        action.setCreatedAt(LocalDateTime.now()); action.setUpdatedAt(LocalDateTime.now()); actionMapper.insert(action);
        log.info("agent_action action_id={} session_id={} user_id={} type={} status=PENDING_CONFIRMATION", action.getId(), sessionId, userId, type);
        return toDto(action);
    }

    /**
     * 确认并执行一项待确认操作。
     * <ol><li>抢占执行</li><li>核验快照</li><li>提交结果</li></ol>
     * @param actionId 待确认操作标识
     * @return 操作执行后的最新状态
     */
    public AgentActionDto confirm(String actionId) {
        Long userId = UserContext.currentUserId();
        try {
            return transactionTemplate.execute(status -> confirmInTransaction(actionId, userId));
        } catch (Exception e) {
            Throwable cause = e instanceof ActionExecutionException && e.getCause() != null ? e.getCause() : e;
            String message = cause instanceof BizException ? cause.getMessage() : "操作执行失败，请稍后重试";
            String result = objectMapper.createObjectNode().put("message", message).toString();
            actionMapper.failPending(actionId, userId, result);
            log.warn("agent_action action_id={} user_id={} status=FAILED cause={}", actionId, userId, cause.getClass().getSimpleName());
            return toDto(requireOwned(actionId, userId));
        }
    }

    /**
     * 在同一事务内抢占并执行确认操作。
     * <ol><li>核验状态</li><li>抢占操作</li><li>比对快照</li></ol>
     *
     * @param actionId 待确认操作标识
     * @param userId 当前用户标识
     * @return 操作的最终状态
     */
    private AgentActionDto confirmInTransaction(String actionId, Long userId) {
        AgentAction action = requireOwned(actionId, userId); LocalDateTime now = LocalDateTime.now();
        if (!AgentActionPolicy.canConfirm(action.getStatus(), action.getExpiresAt(), now)) {
            if ("PENDING_CONFIRMATION".equals(action.getStatus()) && !action.getExpiresAt().isAfter(now)) actionMapper.expirePending(actionId, userId, now);
            return toDto(requireOwned(actionId, userId));
        }
        // 先原子抢占，重复点击或并发确认只能由一个请求进入执行阶段。
        if (actionMapper.claim(actionId, userId) != 1) {
            AgentAction current = requireOwned(actionId, userId);
            if ("PENDING_CONFIRMATION".equals(current.getStatus()) && !current.getExpiresAt().isAfter(LocalDateTime.now())) actionMapper.expirePending(actionId, userId, LocalDateTime.now());
            return toDto(requireOwned(actionId, userId));
        }
        JsonNode payload = parse(action.getPayloadJson());
        String currentFingerprint;
        try { currentFingerprint = fingerprintTarget(action.getActionType(), payload, true); }
        catch (BizException e) { currentFingerprint = null; }
        // 对可变目标重新取锁内快照，账本变化后不执行用户基于旧内容作出的确认。
        if (action.getTargetFingerprint() != null && !action.getTargetFingerprint().equals(currentFingerprint)) {
            action.setStatus("STALE"); action.setUpdatedAt(now); actionMapper.updateById(action);
            log.info("agent_action action_id={} user_id={} status=STALE", actionId, userId);
            return toDto(action);
        }
        try {
            // 只有通过确认和快照校验的意图才会调用实际写入服务。
            Object result = execute(action.getActionType(), payload);
            action.setStatus("EXECUTED"); action.setResultJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) { throw new ActionExecutionException(e); }
        action.setUpdatedAt(LocalDateTime.now()); actionMapper.updateById(action);
        log.info("agent_action action_id={} user_id={} type={} status=EXECUTED", actionId, userId, action.getActionType());
        return toDto(action);
    }

    /**
     * 取消尚未开始执行的操作。
     * <ol><li>校验归属</li><li>更新状态</li></ol>
     * @param actionId 待取消操作标识
     * @return 取消后的最新状态
     * @throws BizException 操作不属于当前用户或不存在时抛出
     */
    public AgentActionDto cancel(String actionId) {
        Long userId = UserContext.currentUserId(); AgentAction action = requireOwned(actionId, userId);
        if ("PENDING_CONFIRMATION".equals(action.getStatus())) {
            if (actionMapper.cancel(actionId, userId) != 1) actionMapper.expirePending(actionId, userId, LocalDateTime.now());
            log.info("agent_action action_id={} user_id={} status=CANCELLED_OR_EXPIRED", actionId, userId);
        }
        return toDto(requireOwned(actionId, userId));
    }

    /**
     * 获取当前用户的一项操作状态。
     * <ol><li>校验归属</li><li>处理过期</li></ol>
     * @param actionId 操作标识
     * @return 已同步过期状态的操作数据
     * @throws BizException 操作不属于当前用户或不存在时抛出
     */
    public AgentActionDto getOwned(String actionId) {
        Long userId = UserContext.currentUserId(); AgentAction action = requireOwned(actionId, userId);
        if ("PENDING_CONFIRMATION".equals(action.getStatus()) && !action.getExpiresAt().isAfter(LocalDateTime.now())) {
            actionMapper.expirePending(action.getId(), userId, LocalDateTime.now());
            action = requireOwned(actionId, userId);
        }
        return toDto(action);
    }

    /**
     * 执行已通过确认校验的账本意图。
     * <ol><li>识别类型</li><li>转换数据</li><li>调用服务</li></ol>
     *
     * @param type 已登记的动作类型
     * @param payload 经确认的动作数据
     * @return 原有账本服务的执行结果
     * @throws Exception 动作数据无法转换或服务执行失败时抛出
     */
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
