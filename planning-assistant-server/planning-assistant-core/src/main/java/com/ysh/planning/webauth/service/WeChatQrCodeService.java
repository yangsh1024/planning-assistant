package com.ysh.planning.webauth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysh.planning.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WeChatQrCodeService {
    private final ObjectMapper objectMapper;
    @Value("${wechat.appid}") private String appid;
    @Value("${wechat.secret}") private String secret;
    @Value("${wechat.dynamic-qr-enabled:false}") private boolean enabled;
    private String cachedToken;
    private LocalDateTime tokenExpiresAt = LocalDateTime.MIN;

    public byte[] qrCode(String requestId) {
        if (!enabled) throw new BizException(404, "动态小程序码未启用");
        try {
            String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken();
            HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
            byte[] body = new RestTemplate().postForObject(url,
                    new HttpEntity<>(Map.of("scene", requestId, "page", "pages/auth/web-login/web-login", "check_path", false, "env_version", "release"), headers), byte[].class);
            if (body == null || body.length < 100) throw new BizException(503, "动态小程序码生成失败");
            if (body[0] == '{') throw new BizException(503, "动态小程序码生成失败");
            return body;
        } catch (BizException e) { throw e; } catch (Exception e) { throw new BizException(503, "动态小程序码生成失败"); }
    }

    private synchronized String accessToken() throws Exception {
        if (cachedToken != null && tokenExpiresAt.isAfter(LocalDateTime.now().plusMinutes(1))) return cachedToken;
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + appid + "&secret=" + secret;
        JsonNode response = objectMapper.readTree(new RestTemplate().getForObject(url, String.class));
        if (!response.hasNonNull("access_token")) throw new BizException(503, "微信 access_token 获取失败");
        cachedToken = response.get("access_token").asText(); tokenExpiresAt = LocalDateTime.now().plusSeconds(response.path("expires_in").asLong(7200)); return cachedToken;
    }
}
