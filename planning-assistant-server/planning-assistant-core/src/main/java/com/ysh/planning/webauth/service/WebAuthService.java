package com.ysh.planning.webauth.service;

import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
import com.ysh.planning.common.security.UserContext;
import com.ysh.planning.webauth.domain.WebLoginRequest;
import com.ysh.planning.webauth.domain.WebSsoTicket;
import com.ysh.planning.webauth.dto.SsoLinkDto;
import com.ysh.planning.webauth.dto.WebLoginRequestDto;
import com.ysh.planning.webauth.dto.WebLoginStatusDto;
import com.ysh.planning.webauth.repository.WebLoginRequestMapper;
import com.ysh.planning.webauth.repository.WebSsoTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
/**
 * 协调小程序确认与浏览器 Cookie 登录的短时凭据。
 * 浏览器仅持有校验用 proof，长期身份只在确认完成后写入 HttpOnly Cookie。
 */
public class WebAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PENDING = "PENDING";
    private final WebLoginRequestMapper loginRequestMapper;
    private final WebSsoTicketMapper ssoTicketMapper;
    private final WebLoginAttemptLimiter attemptLimiter;
    @Value("${app.public-base-url:https://localhost:8080}") private String publicBaseUrl;
    @Value("${wechat.dynamic-qr-enabled:false}") private boolean dynamicQrEnabled;
    @Value("${wechat.fixed-qr-url:}") private String fixedQrUrl;

    /**
     * 创建浏览器发起的小程序确认请求。
     * <ol><li>清理过期</li><li>生成凭据</li><li>选择登录方式</li></ol>
     * @param deviceLabel 浏览器提交的设备标识
     * @return 浏览器轮询和展示登录码所需的信息
     * @throws BizException 未配置可用登录方式或无法生成唯一登录码时抛出
     */
    public WebLoginRequestDto createBrowserLogin(String deviceLabel) {
        LocalDateTime now = LocalDateTime.now();
        if (!dynamicQrEnabled && fixedQrUrl.isBlank()) throw new BizException(503, "固定小程序码尚未配置");
        loginRequestMapper.expireOutstanding(now);
        loginRequestMapper.deleteFinishedBefore(now.minusDays(7));
        String proof = randomToken();
        String fallbackCode = null;
        WebLoginRequest entity = new WebLoginRequest();
        entity.setBrowserProofHash(hash(proof));
        entity.setDeviceLabel(normalizeDeviceLabel(deviceLabel));
        entity.setStatus(PENDING);
        entity.setExpiresAt(now.plusMinutes(2));
        entity.setCreatedAt(now);
        // 登录码只存摘要；碰撞时重试，避免把短码明文写入数据库。
        for (int attempt = 0; attempt < 20; attempt++) {
            fallbackCode = WebLoginCode.format(RANDOM.nextInt(1_000_000));
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setFallbackCodeHash(hash(fallbackCode));
            try { loginRequestMapper.insert(entity); break; }
            catch (DuplicateKeyException collision) { fallbackCode = null; }
        }
        if (fallbackCode == null) throw new BizException(503, "暂时无法创建登录请求，请重试");
        WebLoginRequestDto dto = new WebLoginRequestDto();
        dto.setRequestId(entity.getId()); dto.setBrowserProof(proof);
        if (dynamicQrEnabled) {
            dto.setMode("DYNAMIC_QR");
            dto.setQrCodeUrl(publicBaseUrl.replaceAll("/$", "") + "/api/web-auth/requests/" + entity.getId() + "/qr");
        } else {
            dto.setMode("FALLBACK_CODE");
            dto.setFallbackCode(fallbackCode);
            dto.setFixedQrCodeUrl(fixedQrUrl);
        }
        dto.setExpiresAt(entity.getExpiresAt());
        return dto;
    }

    public WebLoginStatusDto previewForCurrentUser(String requestId) { return status(requestId, null, true); }
    public void ensureQrAvailable(String requestId) { WebLoginRequest request = loginRequestMapper.selectById(requestId); if (request == null) throw new BizException(ErrorCode.NOT_FOUND); expireIfNeeded(request); if (!PENDING.equals(request.getStatus())) throw new BizException(ErrorCode.NOT_FOUND); }
    public WebLoginStatusDto resolveFallbackCode(String code) {
        if (!attemptLimiter.tryAcquire(UserContext.currentUserId(), java.time.Instant.now())) throw new BizException(429, "登录码尝试过于频繁");
        WebLoginRequest request = loginRequestMapper.selectByFallbackCodeHash(hash(code));
        if (request == null) throw new BizException(ErrorCode.NOT_FOUND);
        return statusForEntity(request, true);
    }
    public WebLoginStatusDto browserStatus(String requestId, String browserProof) { return status(requestId, browserProof, false); }

    @Transactional
    public void approve(String requestId) {
        resolvePending(requestId, "APPROVED");
    }
    @Transactional
    public void reject(String requestId) {
        resolvePending(requestId, "REJECTED");
    }
    @Transactional
    /**
     * 以浏览器 proof 交换已确认登录请求。
     * <ol><li>核验凭据</li><li>消费请求</li><li>返回用户</li></ol>
     * @param requestId 登录请求标识
     * @param proof 浏览器持有的一次性校验值
     * @return 已确认登录对应的用户标识
     * @throws BizException proof 无效、请求未确认或已被消费时抛出
     */
    public Long exchangeBrowser(String requestId, String proof) {
        WebLoginRequest request = loginRequestMapper.selectById(requestId);
        if (request == null || !hash(proof).equals(request.getBrowserProofHash())) throw new BizException(ErrorCode.UNAUTHORIZED);
        expireIfNeeded(request);
        if (!"APPROVED".equals(request.getStatus()) || request.getUserId() == null) throw new BizException(409, "登录请求尚未确认或已失效");
        // 原子消费防止同一 proof 被并发兑换为多个浏览器会话。
        int changed = loginRequestMapper.consumeApproved(requestId, LocalDateTime.now());
        if (changed != 1) throw new BizException(409, "登录请求已被使用");
        return request.getUserId();
    }
    @Transactional
    /**
     * 创建供已登录小程序复制到浏览器的一次性登录链接。
     * <ol><li>生成票据</li><li>保存摘要</li><li>返回链接</li></ol>
     * @return 含短时票据的浏览器登录链接
     */
    public SsoLinkDto createSsoLink() {
        String rawTicket = randomToken(); LocalDateTime now = LocalDateTime.now();
        ssoTicketMapper.deleteStaleBefore(now.minusDays(7));
        WebSsoTicket ticket = new WebSsoTicket(); ticket.setId(UUID.randomUUID().toString()); ticket.setUserId(UserContext.currentUserId());
        ticket.setTicketHash(hash(rawTicket)); ticket.setCreatedAt(now); ticket.setExpiresAt(now.plusSeconds(60)); ssoTicketMapper.insert(ticket);
        SsoLinkDto dto = new SsoLinkDto(); dto.setLoginUrl(publicBaseUrl.replaceAll("/$", "") + "/#ticket=" + rawTicket); dto.setExpiresAt(ticket.getExpiresAt()); return dto;
    }
    @Transactional
    /**
     * 消费短链票据并返回其所属用户。
     * <ol><li>查找票据</li><li>原子消费</li><li>返回用户</li></ol>
     * @param rawTicket 浏览器从 URL fragment 取得的原始票据
     * @return 已登录用户标识
     * @throws BizException 票据不存在、过期或已使用时抛出
     */
    public Long exchangeSso(String rawTicket) {
        WebSsoTicket ticket = ssoTicketMapper.selectByTicketHash(hash(rawTicket));
        LocalDateTime now = LocalDateTime.now();
        if (ticket == null || ticket.getConsumedAt() != null || !ticket.getExpiresAt().isAfter(now)
                || ssoTicketMapper.consume(ticket.getId(), now) != 1) {
            throw new BizException(409, "登录链接已失效");
        }
        return ticket.getUserId();
    }
    private WebLoginStatusDto status(String requestId, String proof, boolean authenticated) {
        WebLoginRequest request = loginRequestMapper.selectById(requestId);
        if (request == null || (!authenticated && (proof == null || !hash(proof).equals(request.getBrowserProofHash())))) throw new BizException(ErrorCode.NOT_FOUND);
        return statusForEntity(request, true);
    }
    private WebLoginStatusDto statusForEntity(WebLoginRequest request, boolean expire) {
        if (expire) expireIfNeeded(request); WebLoginStatusDto dto = new WebLoginStatusDto();
        dto.setRequestId(request.getId()); dto.setDeviceLabel(request.getDeviceLabel()); dto.setStatus(request.getStatus()); dto.setExpiresAt(request.getExpiresAt()); return dto;
    }
    private void resolvePending(String requestId, String nextStatus) {
        LocalDateTime now = LocalDateTime.now();
        if (loginRequestMapper.resolvePending(requestId, UserContext.currentUserId(), nextStatus, now) == 1) return;
        WebLoginRequest request = loginRequestMapper.selectById(requestId);
        if (request == null) throw new BizException(ErrorCode.NOT_FOUND);
        expireIfNeeded(request);
        throw new BizException(409, "登录请求不能确认");
    }
    private void expireIfNeeded(WebLoginRequest request) { if ((PENDING.equals(request.getStatus()) || "APPROVED".equals(request.getStatus())) && !request.getExpiresAt().isAfter(LocalDateTime.now())) { request.setStatus("EXPIRED"); request.setFallbackCodeHash(null); loginRequestMapper.updateById(request); } }
    private String randomToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String normalizeDeviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "浏览器设备";
        String lower = userAgent.toLowerCase();
        String browser = lower.contains("micromessenger") ? "微信"
                : lower.contains("edg/") ? "Edge"
                : lower.contains("chrome/") || lower.contains("crios/") ? "Chrome"
                : lower.contains("firefox/") || lower.contains("fxios/") ? "Firefox"
                : lower.contains("safari/") ? "Safari" : "浏览器";
        String os = lower.contains("windows") ? "Windows"
                : lower.contains("iphone") || lower.contains("ipad") ? "iOS"
                : lower.contains("android") ? "Android"
                : lower.contains("macintosh") || lower.contains("mac os") ? "macOS"
                : lower.contains("linux") ? "Linux" : "未知设备";
        return browser + " · " + os;
    }
}
