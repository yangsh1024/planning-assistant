package com.ysh.planning.webauth.controller;

import com.ysh.planning.common.response.Result;
import com.ysh.planning.common.security.JwtUtil;
import com.ysh.planning.common.security.WebCookieAuthenticationFilter;
import com.ysh.planning.common.security.WebCsrfFilter;
import com.ysh.planning.webauth.dto.*;
import com.ysh.planning.webauth.service.WebAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 暴露小程序确认和一次性短链两种 Web 登录入口。
 * 浏览器凭据只在成功交换后以 HttpOnly Cookie 写入，接口不返回长期令牌。
 */
@RestController
@RequestMapping("/api/web-auth")
@RequiredArgsConstructor
public class WebAuthController {
    private final WebAuthService service;
    private final JwtUtil jwtUtil;

    /** 创建浏览器登录请求，并返回轮询所需的凭据。 */
    @PostMapping("/requests")
    public Result<WebLoginRequestDto> create(HttpServletRequest request) {
        return Result.ok(service.createBrowserLogin(request.getHeader("User-Agent")));
    }

    /** 使用浏览器 proof 查询登录请求的公开状态。 */
    @GetMapping("/requests/{id}/status")
    public Result<WebLoginStatusDto> status(@PathVariable String id, @RequestHeader("X-Web-Login-Proof") String proof) {
        return Result.ok(service.browserStatus(id, proof));
    }

    /** 供已登录小程序用户预览待确认的浏览器登录请求。 */
    @GetMapping("/requests/{id}/preview")
    public Result<WebLoginStatusDto> preview(@PathVariable String id) {
        return Result.ok(service.previewForCurrentUser(id));
    }

    /** 使用固定小程序码页面中输入的六位数字码获取待确认请求。 */
    @PostMapping("/login-codes/resolve")
    public Result<WebLoginStatusDto> resolve(@Valid @RequestBody LoginCodeRequest request) {
        return Result.ok(service.resolveLoginCode(request.getLoginCode()));
    }

    /** 确认当前用户拥有的浏览器登录请求。 */
    @PostMapping("/requests/{id}/approve")
    public Result<Void> approve(@PathVariable String id) {
        service.approve(id);
        return Result.ok();
    }

    /** 拒绝当前用户拥有的浏览器登录请求。 */
    @PostMapping("/requests/{id}/reject")
    public Result<Void> reject(@PathVariable String id) {
        service.reject(id);
        return Result.ok();
    }

    /** 消费已确认的浏览器请求，并写入认证与 CSRF Cookie。 */
    @PostMapping("/requests/{id}/exchange")
    public Result<Void> exchange(@PathVariable String id, @Valid @RequestBody BrowserProofRequest request, HttpServletResponse response) {
        issueCookies(service.exchangeBrowser(id, request.getBrowserProof()), response);
        return Result.ok();
    }

    /** 创建供浏览器使用的一次性小程序登录链接。 */
    @PostMapping("/miniapp-links")
    public Result<SsoLinkDto> link() {
        return Result.ok(service.createSsoLink());
    }

    /** 消费一次性短链票据，并写入认证与 CSRF Cookie。 */
    @PostMapping("/sso/exchange")
    public Result<Void> sso(@Valid @RequestBody SsoExchangeRequest request, HttpServletResponse response) {
        issueCookies(service.exchangeSso(request.getTicket()), response);
        return Result.ok();
    }

    /**
     * 向已完成身份确认的浏览器写入认证与 CSRF Cookie。
     * <ol><li>签发认证</li><li>生成 CSRF</li><li>禁止缓存</li></ol>
     *
     * @param userId 已确认登录的用户标识
     * @param response 当前 HTTP 响应
     */
    private void issueCookies(Long userId, HttpServletResponse response) {
        Cookie auth = new Cookie(WebCookieAuthenticationFilter.WEB_AUTH_COOKIE, jwtUtil.generateWebToken(userId));
        auth.setHttpOnly(true);
        auth.setSecure(true);
        auth.setPath("/");
        auth.setMaxAge(8 * 3600);
        auth.setAttribute("SameSite", "Lax");
        response.addCookie(auth);
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        Cookie csrf = new Cookie(WebCsrfFilter.CSRF_COOKIE, HexFormat.of().formatHex(bytes));
        csrf.setSecure(true);
        csrf.setPath("/");
        csrf.setMaxAge(8 * 3600);
        csrf.setAttribute("SameSite", "Lax");
        response.addCookie(csrf);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
