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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/web-auth")
@RequiredArgsConstructor
public class WebAuthController {
    private final WebAuthService service; private final JwtUtil jwtUtil; private final com.ysh.planning.webauth.service.WeChatQrCodeService qrCodeService;
    @PostMapping("/requests") public Result<WebLoginRequestDto> create(HttpServletRequest request) { return Result.ok(service.createBrowserLogin(request.getHeader("User-Agent"))); }
    @GetMapping("/requests/{id}/status") public Result<WebLoginStatusDto> status(@PathVariable String id, @RequestHeader("X-Web-Login-Proof") String proof) { return Result.ok(service.browserStatus(id, proof)); }
    @GetMapping(value="/requests/{id}/qr", produces=MediaType.IMAGE_PNG_VALUE) public ResponseEntity<byte[]> qr(@PathVariable String id) { service.ensureQrAvailable(id); return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(qrCodeService.qrCode(id)); }
    @GetMapping("/requests/{id}/preview") public Result<WebLoginStatusDto> preview(@PathVariable String id) { return Result.ok(service.previewForCurrentUser(id)); }
    @PostMapping("/fallback/resolve") public Result<WebLoginStatusDto> resolve(@Valid @RequestBody FallbackCodeRequest request) { return Result.ok(service.resolveFallbackCode(request.getFallbackCode())); }
    @PostMapping("/requests/{id}/approve") public Result<Void> approve(@PathVariable String id) { service.approve(id); return Result.ok(); }
    @PostMapping("/requests/{id}/reject") public Result<Void> reject(@PathVariable String id) { service.reject(id); return Result.ok(); }
    @PostMapping("/requests/{id}/exchange") public Result<Void> exchange(@PathVariable String id, @Valid @RequestBody BrowserProofRequest request, HttpServletResponse response) { issueCookies(service.exchangeBrowser(id, request.getBrowserProof()), response); return Result.ok(); }
    @PostMapping("/miniapp-links") public Result<SsoLinkDto> link() { return Result.ok(service.createSsoLink()); }
    @PostMapping("/sso/exchange") public Result<Void> sso(@Valid @RequestBody SsoExchangeRequest request, HttpServletResponse response) { issueCookies(service.exchangeSso(request.getTicket()), response); return Result.ok(); }
    private void issueCookies(Long userId, HttpServletResponse response) { Cookie auth = new Cookie(WebCookieAuthenticationFilter.WEB_AUTH_COOKIE, jwtUtil.generateWebToken(userId)); auth.setHttpOnly(true); auth.setSecure(true); auth.setPath("/"); auth.setMaxAge(8 * 3600); auth.setAttribute("SameSite", "Lax"); response.addCookie(auth); byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes); Cookie csrf = new Cookie(WebCsrfFilter.CSRF_COOKIE, HexFormat.of().formatHex(bytes)); csrf.setSecure(true); csrf.setPath("/"); csrf.setMaxAge(8 * 3600); csrf.setAttribute("SameSite", "Lax"); response.addCookie(csrf); response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store"); }
}
