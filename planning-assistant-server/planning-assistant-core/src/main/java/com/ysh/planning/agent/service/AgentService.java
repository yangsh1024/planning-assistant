package com.ysh.planning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ysh.planning.agent.domain.AgentMessage;
import com.ysh.planning.agent.domain.AgentSession;
import com.ysh.planning.agent.dto.AgentChatRequest;
import com.ysh.planning.agent.dto.AgentMessageDto;
import com.ysh.planning.agent.dto.AgentSessionDto;
import com.ysh.planning.agent.gateway.AgentModelGateway;
import com.ysh.planning.agent.repository.AgentMessageMapper;
import com.ysh.planning.agent.repository.AgentSessionMapper;
import com.ysh.planning.agent.policy.AgentRateLimiter;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
import com.ysh.planning.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;

import org.slf4j.MDC;
import com.ysh.planning.common.web.RequestIdFilter;

/**
 * 编排 Agent 对话的会话上下文、消息留存与 SSE 响应。
 * 仅向浏览器暴露可见回答和待确认操作，不传递模型推理或工具原始结果。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {
    private static final int MAX_VISIBLE_MESSAGE_CHARS = 15_000;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentModelGateway gateway;
    private final AgentActionService actionService;
    private final AgentRateLimiter rateLimiter;
    @Value("${agent.timeout-seconds:60}")
    private int timeoutSeconds;

    /**
     * 发起一轮流式对话。
     * <ol><li>限流校验</li><li>定位会话</li><li>启动推流</li></ol>
     *
     * @param request 本轮消息及会话选项
     * @return 用于接收公开对话事件的 SSE 通道
     * @throws BizException 消息过于频繁、重试上下文无效或回复超出展示上限时抛出
     */
    public SseEmitter chat(AgentChatRequest request) {
        Long userId = UserContext.currentUserId();
        // 以用户为粒度限流，避免单个账号占满模型服务容量。
        if (!rateLimiter.tryAcquire(userId, Instant.now())) throw new BizException(429, "消息发送过于频繁，请稍后再试");
        AgentSession session = request.getRetryUserMessageId() == null || request.getRetryUserMessageId().isBlank()
                ? loadOrCreate(userId, request.getSessionId(), request.getMessage()) : ensureRetrySession(request, userId);
        AgentMessage user = request.getRetryUserMessageId() == null || request.getRetryUserMessageId().isBlank()
                ? saveMessage(session.getId(), userId, "USER", request.getMessage(), null, "COMPLETED") : requireRetryUserMessage(request, session, userId);
        List<AgentModelGateway.AgentPromptMessage> prompt = context(session.getId(), userId);
        SseEmitter emitter = new SseEmitter((timeoutSeconds + 10L) * 1000L);
        String requestId = Optional.ofNullable(MDC.get(RequestIdFilter.MDC_KEY)).orElse("unknown");
        // 连接生命周期与模型请求绑定，客户端离开后立即停止上游消耗。
        Thread worker = Thread.ofVirtual().unstarted(() -> runStream(emitter, session, user, userId, request, prompt, requestId));
        Runnable cancel = () -> gateway.cancel(worker);
        emitter.onTimeout(cancel);
        emitter.onError(ignored -> cancel.run());
        emitter.onCompletion(cancel);
        worker.start();
        return emitter;
    }

    /**
     * 获取当前用户可访问的历史会话。
     * <ol><li>确定用户</li><li>转换会话</li></ol>
     *
     * @return 当前用户的会话摘要
     */
    public List<AgentSessionDto> sessions() {
        Long userId = UserContext.currentUserId();
        return sessionMapper.selectByUserId(userId).stream().map(this::toSessionDto).toList();
    }

    /**
     * 获取指定会话的可见消息。
     * <ol><li>校验归属</li><li>转换消息</li></ol>
     *
     * @param sessionId 会话标识
     * @return 会话中的用户可见消息
     * @throws BizException 会话不属于当前用户或不存在时抛出
     */
    public List<AgentMessageDto> messages(String sessionId) {
        Long userId = UserContext.currentUserId();
        ensureSession(sessionId, userId);
        return messageMapper.selectVisibleBySession(sessionId, userId).stream().map(this::toMessageDto).toList();
    }

    private void runStream(SseEmitter emitter, AgentSession session, AgentMessage userMessage, Long userId, AgentChatRequest request, List<AgentModelGateway.AgentPromptMessage> prompt, String requestId) {
        StringBuilder answer = new StringBuilder();
        String messageId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        AtomicReference<com.ysh.planning.agent.dto.AgentActionDto> actionRef = new AtomicReference<>();
        try {
            // 虚拟线程不继承请求安全上下文，补齐身份以复用既有业务服务的权限边界。
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_WEB"))));
            emit(emitter, "message_start", Map.of("sessionId", session.getId(), "messageId", messageId, "userMessageId", userMessage.getId()));
            AgentModelGateway.GatewayResult gatewayResult = gateway.stream(prompt, request.isThinkingEnabled(), session.getId(),
                    delta -> appendAndEmitDelta(answer, delta, emitter, messageId),
                    action -> {
                        actionRef.set(action);
                        emit(emitter, "action_required", action);
                    });
            String content = answer.isEmpty() && actionRef.get() != null ? "请确认以下操作" : answer.toString();
            AgentMessage saved = saveMessage(messageId, session.getId(), userId, "ASSISTANT", content,
                    gatewayResult.model(), gatewayResult.inputTokens(), gatewayResult.outputTokens(), "COMPLETED");
            // 将确认卡片归属到回答消息，历史记录才能恢复待确认状态。
            if (actionRef.get() != null) {
                saved.setActionId(actionRef.get().getActionId());
                messageMapper.updateById(saved);
            }
            touch(session);
            emit(emitter, "done", Map.of("messageId", messageId));
            emitter.complete();
            log.info("agent_request request_id={} session_id={} duration_ms={} status=COMPLETED input_tokens={} output_tokens={}", requestId, session.getId(), elapsedMillis(startedAt), gatewayResult.inputTokens(), gatewayResult.outputTokens());
        } catch (Exception e) {
            // 保留已生成的可见片段，用户可据此决定是否重试。
            try {
                saveMessage(messageId, session.getId(), userId, "ASSISTANT", capped(answer.toString()), null, null, null, "FAILED");
            } catch (Exception ignored) {
            }
            safeEmit(emitter, "error", Map.of("code", "AGENT_UNAVAILABLE", "retryable", true));
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
            }
            log.info("agent_request request_id={} session_id={} duration_ms={} status=FAILED input_tokens=0 output_tokens=0", requestId, session.getId(), elapsedMillis(startedAt));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private List<AgentModelGateway.AgentPromptMessage> context(String sessionId, Long userId) {
        List<AgentMessage> all = messageMapper.selectVisibleBySession(sessionId, userId).stream().filter(m -> "COMPLETED".equals(m.getStatus())).toList();
        List<AgentMessage> recent = all.size() > 20 ? all.subList(all.size() - 20, all.size()) : all;
        int chars = 0;
        List<AgentModelGateway.AgentPromptMessage> out = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            AgentMessage m = recent.get(i);
            chars += m.getContent().length();
            if (chars > 30_000) break;
            out.addFirst(new AgentModelGateway.AgentPromptMessage("USER".equals(m.getRole()) ? "user" : "assistant", m.getContent()));
        }
        return out;
    }

    private AgentSession loadOrCreate(Long userId, String sessionId, String firstMessage) {
        if (sessionId != null && !sessionId.isBlank()) return ensureSession(sessionId, userId);
        AgentSession session = new AgentSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTitle(firstMessage.substring(0, Math.min(40, firstMessage.length())));
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    private AgentSession ensureRetrySession(AgentChatRequest request, Long userId) {
        if (request.getSessionId() == null || request.getSessionId().isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "重试必须指定原会话");
        return ensureSession(request.getSessionId(), userId);
    }

    private AgentMessage requireRetryUserMessage(AgentChatRequest request, AgentSession session, Long userId) {
        AgentMessage message = messageMapper.selectOne(new LambdaQueryWrapper<AgentMessage>().eq(AgentMessage::getId, request.getRetryUserMessageId()).eq(AgentMessage::getSessionId, session.getId()).eq(AgentMessage::getUserId, userId).eq(AgentMessage::getRole, "USER").eq(AgentMessage::getStatus, "COMPLETED"));
        if (message == null || !message.getContent().equals(request.getMessage()))
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "无法重试该消息");
        return message;
    }

    private AgentSession ensureSession(String id, Long userId) {
        AgentSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AgentSession>().eq(AgentSession::getId, id).eq(AgentSession::getUserId, userId));
        if (session == null) throw new BizException(ErrorCode.NOT_FOUND);
        return session;
    }

    private AgentMessage saveMessage(String sessionId, Long userId, String role, String content, String model, String status) {
        return saveMessage(UUID.randomUUID().toString(), sessionId, userId, role, content, model, null, null, status);
    }

    private AgentMessage saveMessage(String id, String sessionId, Long userId, String role, String content, String model, Integer inputTokens, Integer outputTokens, String status) {
        AgentMessage message = new AgentMessage();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setModel(model);
        message.setInputTokens(inputTokens);
        message.setOutputTokens(outputTokens);
        message.setStatus(status);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    private void touch(AgentSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private AgentSessionDto toSessionDto(AgentSession s) {
        AgentSessionDto dto = new AgentSessionDto();
        dto.setSessionId(s.getId());
        dto.setTitle(s.getTitle());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }

    private AgentMessageDto toMessageDto(AgentMessage m) {
        AgentMessageDto dto = new AgentMessageDto();
        dto.setMessageId(m.getId());
        dto.setRole(m.getRole());
        dto.setContent(m.getContent());
        if (m.getActionId() != null) dto.setAction(actionService.getOwned(m.getActionId()));
        dto.setStatus(m.getStatus());
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }

    private void appendAndEmitDelta(StringBuilder answer, String delta, SseEmitter emitter, String messageId) {
        int remaining = MAX_VISIBLE_MESSAGE_CHARS - answer.length();
        if (remaining <= 0) throw new BizException(503, "Agent 回复过长，请缩小问题范围后重试");
        String accepted = delta.length() <= remaining ? delta : delta.substring(0, remaining);
        answer.append(accepted);
        if (!accepted.isEmpty()) emit(emitter, "delta", Map.of("messageId", messageId, "text", accepted));
        if (accepted.length() != delta.length()) throw new BizException(503, "Agent 回复过长，请缩小问题范围后重试");
    }

    private String capped(String content) {
        return content.length() <= MAX_VISIBLE_MESSAGE_CHARS ? content : content.substring(0, MAX_VISIBLE_MESSAGE_CHARS);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void emit(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            throw new ClientDisconnectedException();
        }
    }

    private void safeEmit(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException ignored) {
        }
    }

    private static final class ClientDisconnectedException extends RuntimeException {
    }
}
