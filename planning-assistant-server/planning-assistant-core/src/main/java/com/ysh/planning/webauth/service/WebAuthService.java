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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 协调小程序确认与浏览器 Cookie 登录的短时凭据。
 * 浏览器仅持有校验用 proof，长期身份只在确认完成后写入 HttpOnly Cookie。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PENDING = "PENDING";
    private final WebLoginRequestMapper loginRequestMapper;
    private final WebSsoTicketMapper ssoTicketMapper;
    private final WebLoginAttemptLimiter attemptLimiter;
    @Value("${app.public-base-url:https://localhost:8080}")
    private String publicBaseUrl;
    @Value("${app.fixed-qr-url:}")
    private String fixedQrUrl;

    /**
     * 创建浏览器发起的小程序确认请求。
     * <ol><li>清理过期</li><li>生成六码摘要</li><li>返回固定小程序码地址</li></ol>
     *
     * @param deviceLabel 浏览器提交的设备标识
     * @return 浏览器轮询和展示固定小程序码所需的信息
     */
    public WebLoginRequestDto createBrowserLogin(String deviceLabel) {
        LocalDateTime now = LocalDateTime.now();
        if (fixedQrUrl.isBlank()) throw new BizException(503, "固定小程序码尚未配置");
        loginRequestMapper.expireOutstanding(now);
        loginRequestMapper.deleteFinishedBefore(now.minusDays(7));
        String proof = randomToken();
        WebLoginRequest entity = new WebLoginRequest();
        entity.setBrowserProofHash(hash(proof));
        entity.setDeviceLabel(normalizeDeviceLabel(deviceLabel));
        entity.setStatus(PENDING);
        entity.setExpiresAt(now.plusMinutes(2));
        entity.setCreatedAt(now);
        String loginCode = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            loginCode = WebLoginCode.format(RANDOM.nextInt(1_000_000));
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setLoginCodeHash(hash(loginCode));
            try {
                if (loginRequestMapper.insert(entity) == 1) break;
                loginCode = null;
            } catch (DuplicateKeyException collision) {
                loginCode = null;
            }
        }
        if (loginCode == null) throw new BizException(503, "暂时无法创建登录请求，请重试");
        log.info("web_login_request request_id={} status=CREATED", entity.getId());
        WebLoginRequestDto dto = new WebLoginRequestDto();
        dto.setRequestId(entity.getId());
        dto.setBrowserProof(proof);
        dto.setMode("FIXED_QR_CODE");
        dto.setLoginCode(loginCode);
        dto.setFixedQrCodeUrl(fixedQrUrl);
        dto.setExpiresAt(entity.getExpiresAt());
        return dto;
    }

    /**
     * 供已登录小程序用户查看登录请求详情。
     *
     * @param requestId 登录请求标识
     * @return 可展示的登录状态
     */
    public WebLoginStatusDto previewForCurrentUser(String requestId) {
        return status(requestId, null, true);
    }

    /**
     * 使用六位登录码定位当前用户待确认的浏览器请求。
     */
    public WebLoginStatusDto resolveLoginCode(String loginCode) {
        if (!attemptLimiter.tryAcquire(UserContext.currentUserId(), java.time.Instant.now())) {
            throw new BizException(429, "登录码尝试过于频繁");
        }
        WebLoginRequest request = loginRequestMapper.selectByLoginCodeHash(hash(loginCode));
        if (request == null) throw new BizException(ErrorCode.NOT_FOUND);
        return statusForEntity(request, true);
    }

    /**
     * 使用浏览器持有的 proof 查询登录进度。
     *
     * @param requestId    登录请求标识
     * @param browserProof 浏览器一次性校验值
     * @return 可公开的登录状态
     */
    public WebLoginStatusDto browserStatus(String requestId, String browserProof) {
        return status(requestId, browserProof, false);
    }

    /**
     * 确认当前用户的一笔待处理浏览器登录请求。
     *
     * @param requestId 登录请求标识
     */
    @Transactional
    public void approve(String requestId) {
        resolvePending(requestId, "APPROVED");
    }

    /**
     * 拒绝当前用户的一笔待处理浏览器登录请求。
     *
     * @param requestId 登录请求标识
     */
    @Transactional
    public void reject(String requestId) {
        resolvePending(requestId, "REJECTED");
    }

    /**
     * 以浏览器 proof 交换已确认登录请求。
     * <ol><li>核验凭据</li><li>消费请求</li><li>返回用户</li></ol>
     *
     * @param requestId 登录请求标识
     * @param proof     浏览器持有的一次性校验值
     * @return 已确认登录对应的用户标识
     * @throws BizException proof 无效、请求未确认或已被消费时抛出
     */
    @Transactional
    public Long exchangeBrowser(String requestId, String proof) {
        WebLoginRequest request = loginRequestMapper.selectById(requestId);
        if (request == null || !hash(proof).equals(request.getBrowserProofHash()))
            throw new BizException(ErrorCode.UNAUTHORIZED);
        expireIfNeeded(request);
        if (!"APPROVED".equals(request.getStatus()) || request.getUserId() == null)
            throw new BizException(409, "登录请求尚未确认或已失效");
        // 原子消费防止同一 proof 被并发兑换为多个浏览器会话。
        int changed = loginRequestMapper.consumeApproved(requestId, LocalDateTime.now());
        if (changed != 1) throw new BizException(409, "登录请求已被使用");
        log.info("web_login_request request_id={} user_id={} status=CONSUMED", requestId, request.getUserId());
        return request.getUserId();
    }

    /**
     * 创建供已登录小程序复制到浏览器的一次性登录链接。
     * <ol><li>生成票据</li><li>保存摘要</li><li>返回链接</li></ol>
     *
     * @return 含短时票据的浏览器登录链接
     */
    @Transactional
    public SsoLinkDto createSsoLink() {
        String rawTicket = randomToken();
        LocalDateTime now = LocalDateTime.now();
        ssoTicketMapper.deleteStaleBefore(now.minusDays(7));
        WebSsoTicket ticket = new WebSsoTicket();
        ticket.setId(UUID.randomUUID().toString());
        ticket.setUserId(UserContext.currentUserId());
        ticket.setTicketHash(hash(rawTicket));
        ticket.setCreatedAt(now);
        ticket.setExpiresAt(now.plusSeconds(60));
        ssoTicketMapper.insert(ticket);
        log.info("web_sso_ticket ticket_id={} user_id={} status=CREATED", ticket.getId(), ticket.getUserId());
        SsoLinkDto dto = new SsoLinkDto();
        dto.setLoginUrl(publicBaseUrl.replaceAll("/$", "") + "/#ticket=" + rawTicket);
        dto.setExpiresAt(ticket.getExpiresAt());
        return dto;
    }

    /**
     * 消费短链票据并返回其所属用户。
     * <ol><li>查找票据</li><li>原子消费</li><li>返回用户</li></ol>
     *
     * @param rawTicket 浏览器从 URL fragment 取得的原始票据
     * @return 已登录用户标识
     * @throws BizException 票据不存在、过期或已使用时抛出
     */
    @Transactional
    public Long exchangeSso(String rawTicket) {
        WebSsoTicket ticket = ssoTicketMapper.selectByTicketHash(hash(rawTicket));
        LocalDateTime now = LocalDateTime.now();
        if (ticket == null || ticket.getConsumedAt() != null || !ticket.getExpiresAt().isAfter(now)
                || ssoTicketMapper.consume(ticket.getId(), now) != 1) {
            throw new BizException(409, "登录链接已失效");
        }
        log.info("web_sso_ticket ticket_id={} user_id={} status=CONSUMED", ticket.getId(), ticket.getUserId());
        return ticket.getUserId();
    }

    /**
     * 返回浏览器或小程序可查看的登录请求状态。
     * <ol><li>核验访问</li><li>处理过期</li><li>转换状态</li></ol>
     *
     * @param requestId     登录请求标识
     * @param proof         浏览器持有的校验值
     * @param authenticated 是否已由小程序身份访问
     * @return 可公开的登录状态
     * @throws BizException 请求不存在或浏览器 proof 不匹配时抛出
     */
    private WebLoginStatusDto status(String requestId, String proof, boolean authenticated) {
        WebLoginRequest request = loginRequestMapper.selectById(requestId);
        if (request == null || (!authenticated && (proof == null || !hash(proof).equals(request.getBrowserProofHash()))))
            throw new BizException(ErrorCode.NOT_FOUND);
        return statusForEntity(request, true);
    }

    /**
     * 将登录请求实体转换为不含敏感凭据的状态对象。
     *
     * @param request 登录请求实体
     * @param expire  是否先更新过期状态
     * @return 可公开的登录状态
     */
    private WebLoginStatusDto statusForEntity(WebLoginRequest request, boolean expire) {
        if (expire) expireIfNeeded(request);
        WebLoginStatusDto dto = new WebLoginStatusDto();
        dto.setRequestId(request.getId());
        dto.setDeviceLabel(request.getDeviceLabel());
        dto.setStatus(request.getStatus());
        dto.setExpiresAt(request.getExpiresAt());
        return dto;
    }

    /**
     * 将当前用户的待确认登录请求原子地更新为目标状态。
     * <ol><li>抢占请求</li><li>处理过期</li><li>拒绝冲突</li></ol>
     *
     * @param requestId  登录请求标识
     * @param nextStatus APPROVED 或 REJECTED 状态
     * @throws BizException 请求不存在、过期或已被处理时抛出
     */
    private void resolvePending(String requestId, String nextStatus) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.currentUserId();
        if (loginRequestMapper.resolvePending(requestId, userId, nextStatus, now) == 1) {
            log.info("web_login_request request_id={} user_id={} status={}", requestId, userId, nextStatus);
            return;
        }
        WebLoginRequest request = loginRequestMapper.selectById(requestId);
        if (request == null) throw new BizException(ErrorCode.NOT_FOUND);
        expireIfNeeded(request);
        throw new BizException(409, "登录请求不能确认");
    }

    /**
     * 将超时且尚可处理的登录请求更新为过期状态。
     *
     * @param request 待检查的登录请求
     */
    private void expireIfNeeded(WebLoginRequest request) {
        if ((PENDING.equals(request.getStatus()) || "APPROVED".equals(request.getStatus())) && !request.getExpiresAt().isAfter(LocalDateTime.now())) {
            request.setStatus("EXPIRED");
            request.setLoginCodeHash(null);
            loginRequestMapper.updateById(request);
            log.info("web_login_request request_id={} status=EXPIRED", request.getId());
        }
    }

    /**
     * 生成高熵的一次性原始凭据。
     *
     * @return 十六进制凭据
     */
    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 计算凭据摘要，避免持久化原始凭据。
     *
     * @param value 原始凭据
     * @return SHA-256 十六进制摘要
     */
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 从浏览器 User-Agent 生成可供用户识别的设备标签。
     *
     * @param userAgent 浏览器提交的 User-Agent
     * @return 浏览器与操作系统组成的标签
     */
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
