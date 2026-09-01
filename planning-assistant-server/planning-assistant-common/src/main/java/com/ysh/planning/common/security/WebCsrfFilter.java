package com.ysh.planning.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Set;

/**
 * 校验携带 Web 身份 Cookie 的非安全请求。
 * 使用双提交 Cookie，阻止第三方页面借用浏览器自动附带的身份 Cookie 发起写操作。
 */
@Component
@Slf4j
public class WebCsrfFilter extends OncePerRequestFilter {
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    /**
     * 拒绝未携带匹配 CSRF Token 的浏览器写请求。
     * <ol><li>判断来源</li><li>比对令牌</li><li>继续请求</li></ol>
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
        // 仅 Web Cookie 请求需要该校验，小程序 Bearer Token 不走浏览器 CSRF 威胁模型。
        if (!SAFE_METHODS.contains(request.getMethod()) && hasWebCookie(request)) {
            String expected = cookieValue(request, CSRF_COOKIE);
            String actual = request.getHeader("X-CSRF-TOKEN");
            if (expected == null || !expected.equals(actual)) {
                log.warn("csrf_validation method={} path={} status=REJECTED", request.getMethod(), request.getRequestURI());
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"code\":403,\"message\":\"CSRF 校验失败\",\"data\":null}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 判断请求是否携带 Web 身份 Cookie。
     * <ol><li>读取 Cookie</li><li>确认身份</li></ol>
     *
     * @param request 当前 HTTP 请求
     * @return 携带 Web 身份 Cookie 时为 {@code true}
     */
    private boolean hasWebCookie(HttpServletRequest request) { return cookieValue(request, WebCookieAuthenticationFilter.WEB_AUTH_COOKIE) != null; }

    /**
     * 查找指定名称的 Cookie 值。
     * <ol><li>读取 Cookie</li><li>匹配名称</li><li>返回值</li></ol>
     *
     * @param request 当前 HTTP 请求
     * @param name Cookie 名称
     * @return Cookie 值；不存在时为 {@code null}
     */
    private String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
