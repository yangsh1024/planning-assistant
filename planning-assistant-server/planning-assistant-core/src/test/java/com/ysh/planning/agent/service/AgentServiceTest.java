package com.ysh.planning.agent.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ysh.planning.agent.domain.AgentMessage;
import com.ysh.planning.agent.domain.AgentSession;
import com.ysh.planning.agent.dto.AgentChatRequest;
import com.ysh.planning.agent.gateway.AgentModelGateway;
import com.ysh.planning.agent.policy.AgentRateLimiter;
import com.ysh.planning.agent.repository.AgentMessageMapper;
import com.ysh.planning.agent.repository.AgentSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {
    @Mock private AgentSessionMapper sessionMapper;
    @Mock private AgentMessageMapper messageMapper;
    @Mock private AgentModelGateway gateway;
    @Mock private AgentActionService actionService;
    @Mock private AgentRateLimiter rateLimiter;
    private AgentService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
        service = new AgentService(sessionMapper, messageMapper, gateway, actionService, rateLimiter);
        ReflectionTestUtils.setField(service, "timeoutSeconds", 2);
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void retryReusesOwnedUserMessageInsteadOfPersistingDuplicate() {
        AgentSession session = new AgentSession(); session.setId("session"); session.setUserId(7L); session.setUpdatedAt(LocalDateTime.now());
        AgentMessage original = new AgentMessage(); original.setId("user-message"); original.setSessionId("session"); original.setUserId(7L);
        original.setRole("USER"); original.setContent("查询本月预算"); original.setStatus("COMPLETED");
        when(rateLimiter.tryAcquire(eq(7L), any(Instant.class))).thenReturn(true);
        when(sessionMapper.selectOne(any(Wrapper.class))).thenReturn(session);
        when(messageMapper.selectOne(any(Wrapper.class))).thenReturn(original);
        when(messageMapper.selectVisibleBySession("session", 7L)).thenReturn(List.of(original));
        when(gateway.stream(anyList(), eq(false), eq("session"), any(), any()))
                .thenReturn(new AgentModelGateway.GatewayResult("deepseek-v4-flash", 1, 1));
        AgentChatRequest request = new AgentChatRequest(); request.setSessionId("session"); request.setMessage("查询本月预算"); request.setRetryUserMessageId("user-message");

        service.chat(request);

        ArgumentCaptor<AgentMessage> inserted = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper, timeout(1000).times(1)).insert(inserted.capture());
        assertThat(inserted.getValue().getRole()).isEqualTo("ASSISTANT");
    }
}
