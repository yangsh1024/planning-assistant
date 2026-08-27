package com.ysh.planning.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WebCsrfFilterTest {
    private final WebCsrfFilter filter = new WebCsrfFilter();

    @Test
    void rejectsUnsafeCookieRequestWithoutMatchingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/chat");
        request.setCookies(new Cookie(WebCookieAuthenticationFilter.WEB_AUTH_COOKIE, "web-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void acceptsUnsafeCookieRequestWithMatchingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/chat");
        request.setCookies(new Cookie(WebCookieAuthenticationFilter.WEB_AUTH_COOKIE, "web-token"), new Cookie(WebCsrfFilter.CSRF_COOKIE, "csrf-token"));
        request.addHeader("X-CSRF-TOKEN", "csrf-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesMiniappBearerRequestsUnaffected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/expense");
        request.addHeader("Authorization", "Bearer miniapp-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
