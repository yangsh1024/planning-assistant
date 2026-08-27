package com.ysh.planning.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationSourceFilterTest {
    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void marksBearerAuthenticationAsMiniapp() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.parseUserId("mini-token")).thenReturn(7L);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/expense");
        request.addHeader("Authorization", "Bearer mini-token");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_MINIAPP");
    }

    @Test
    void marksCookieAuthenticationAsWeb() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.parseWebUserId("web-token")).thenReturn(7L);
        WebCookieAuthenticationFilter filter = new WebCookieAuthenticationFilter(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/sessions");
        request.setCookies(new Cookie(WebCookieAuthenticationFilter.WEB_AUTH_COOKIE, "web-token"));

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_WEB");
    }
}
