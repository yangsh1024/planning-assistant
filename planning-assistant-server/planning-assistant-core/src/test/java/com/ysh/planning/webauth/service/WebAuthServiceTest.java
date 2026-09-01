package com.ysh.planning.webauth.service;

import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.webauth.domain.WebLoginRequest;
import com.ysh.planning.webauth.domain.WebSsoTicket;
import com.ysh.planning.webauth.dto.WebLoginRequestDto;
import com.ysh.planning.webauth.repository.WebLoginRequestMapper;
import com.ysh.planning.webauth.repository.WebSsoTicketMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebAuthServiceTest {
    @Mock private WebLoginRequestMapper loginRequestMapper;
    @Mock private WebSsoTicketMapper ssoTicketMapper;
    @Mock private WebLoginAttemptLimiter attemptLimiter;
    private WebAuthService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
        service = new WebAuthService(loginRequestMapper, ssoTicketMapper, attemptLimiter);
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://ledger.example");
        ReflectionTestUtils.setField(service, "fixedQrUrl", "https://ledger.example/miniapp-web-login-qr.png");
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void createsFixedQrLogin() {
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36";
        when(loginRequestMapper.insert(any(WebLoginRequest.class))).thenReturn(1);

        WebLoginRequestDto result = service.createBrowserLogin(userAgent);

        ArgumentCaptor<WebLoginRequest> captor = ArgumentCaptor.forClass(WebLoginRequest.class);
        verify(loginRequestMapper).insert(captor.capture());
        assertThat(captor.getValue().getDeviceLabel()).isEqualTo("Chrome · macOS");
        assertThat(captor.getValue().getLoginCodeHash()).hasSize(64);
        assertThat(captor.getValue().getLoginCodeHash()).isNotEqualTo(result.getLoginCode());
        assertThat(result.getMode()).isEqualTo("FIXED_QR_CODE");
        assertThat(result.getLoginCode()).matches("\\d{6}");
        assertThat(result.getFixedQrCodeUrl()).isEqualTo("https://ledger.example/miniapp-web-login-qr.png");
    }

    @Test
    void rejectsConcurrentReuseWhenAtomicTicketConsumeLosesRace() {
        WebSsoTicket ticket = new WebSsoTicket(); ticket.setId("ticket-id"); ticket.setUserId(7L);
        ticket.setExpiresAt(LocalDateTime.now().plusSeconds(30));
        when(ssoTicketMapper.selectByTicketHash(anyString())).thenReturn(ticket);
        when(ssoTicketMapper.consume(eq("ticket-id"), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> service.exchangeSso("raw-ticket"))
                .isInstanceOf(BizException.class).hasMessageContaining("失效");
    }

    @Test
    void resolvesLoginApprovalWithSingleConditionalUpdate() {
        when(loginRequestMapper.resolvePending(eq("request-id"), eq(7L), eq("APPROVED"), any(LocalDateTime.class))).thenReturn(1);

        service.approve("request-id");

        verify(loginRequestMapper, never()).updateById(any(WebLoginRequest.class));
    }

    @Test
    void rejectsBrowserExchangeWhenApprovedRequestHasExpired() throws Exception {
        WebLoginRequest request = new WebLoginRequest(); request.setId("request-id"); request.setUserId(7L); request.setStatus("APPROVED");
        request.setBrowserProofHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("proof".getBytes(StandardCharsets.UTF_8))));
        request.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(loginRequestMapper.selectById("request-id")).thenReturn(request);

        assertThatThrownBy(() -> service.exchangeBrowser("request-id", "proof"))
                .isInstanceOf(BizException.class).hasMessageContaining("失效");

        verify(loginRequestMapper, never()).consumeApproved(anyString(), any(LocalDateTime.class));
    }
}
