package com.ysh.planning.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class WebCsrfFilter extends OncePerRequestFilter {
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod()) && hasWebCookie(request)) {
            String expected = cookieValue(request, CSRF_COOKIE);
            String actual = request.getHeader("X-CSRF-TOKEN");
            if (expected == null || !expected.equals(actual)) {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"code\":403,\"message\":\"CSRF 校验失败\",\"data\":null}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasWebCookie(HttpServletRequest request) { return cookieValue(request, WebCookieAuthenticationFilter.WEB_AUTH_COOKIE) != null; }
    private String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
