package com.ysh.planning.common.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从 HttpOnly Web Cookie 恢复浏览器身份，且不覆盖已有小程序认证结果。
 * 这样同一安全链可按凭据来源限制 Web Agent 与小程序账本接口。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebCookieAuthenticationFilter extends OncePerRequestFilter {
    public static final String WEB_AUTH_COOKIE = "WEB_AUTH";
    private final JwtUtil jwtUtil;

    /**
     * 在未认证请求中恢复 Web Cookie 身份。
     * <ol><li>检查上下文</li><li>查找 Cookie</li><li>建立身份</li></ol>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain 后续过滤链
     * @throws ServletException 过滤链处理失败时抛出
     * @throws IOException 请求流处理失败时抛出
     */
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
                    } catch (JwtException e) {
                        log.debug("web_auth status=REJECTED reason=INVALID_COOKIE");
                        SecurityContextHolder.clearContext();
                    }
                    break;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
