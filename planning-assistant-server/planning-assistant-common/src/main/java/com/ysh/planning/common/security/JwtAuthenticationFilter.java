package com.ysh.planning.common.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 识别小程序 Bearer JWT，并建立只适用于小程序接口的身份上下文。
 * Web Cookie 认证由独立过滤器处理，避免认证来源混用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    /**
     * 恢复小程序请求的认证主体。
     * <ol><li>提取令牌</li><li>核验用途</li><li>继续请求</li></ol>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain 后续过滤链
     * @throws ServletException 过滤链处理失败时抛出
     * @throws IOException 请求流处理失败时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        // 无效令牌不保留任何身份，后续权限规则会统一拒绝请求。
        if (StringUtils.hasText(token)) {
            try {
                Long userId = jwtUtil.parseUserId(token);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_MINIAPP")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                log.debug("miniapp_auth status=REJECTED reason=INVALID_TOKEN");
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 提取 Bearer 格式的小程序令牌。
     * <ol><li>读取请求头</li><li>核对前缀</li><li>返回令牌</li></ol>
     *
     * @param request 当前 HTTP 请求
     * @return 有效 Bearer 值；未携带时为 {@code null}
     */
    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
