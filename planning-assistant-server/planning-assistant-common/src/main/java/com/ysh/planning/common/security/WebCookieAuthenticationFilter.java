package com.ysh.planning.common.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 从 HttpOnly Web Cookie 恢复浏览器身份，且不覆盖已有小程序认证结果。 */
@Component
@RequiredArgsConstructor
public class WebCookieAuthenticationFilter extends OncePerRequestFilter {
    public static final String WEB_AUTH_COOKIE = "WEB_AUTH";
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 优先保留已建立的认证，防止 Cookie 改变小程序接口的权限来源。
        if (SecurityContextHolder.getContext().getAuthentication() == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (WEB_AUTH_COOKIE.equals(cookie.getName())) {
                    try {
                        Long userId = jwtUtil.parseWebUserId(cookie.getValue());
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_WEB"))));
                    } catch (JwtException ignored) {
                        SecurityContextHolder.clearContext();
                    }
                    break;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
