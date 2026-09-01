package com.ysh.planning.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
import com.ysh.planning.common.logging.SafeLogException;
import com.ysh.planning.common.security.JwtUtil;
import com.ysh.planning.common.security.UserContext;
import com.ysh.planning.user.domain.AvatarCatalog;
import com.ysh.planning.user.domain.User;
import com.ysh.planning.user.dto.LoginRequest;
import com.ysh.planning.user.dto.LoginResponse;
import com.ysh.planning.user.dto.UpdateProfileRequest;
import com.ysh.planning.user.dto.UserProfileDto;
import com.ysh.planning.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;

/** 处理微信登录、账户初始化和个人资料更新。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${wechat.appid}")
    private String appid;

    @Value("${wechat.secret}")
    private String wechatSecret;

    /**
     * 使用微信登录码完成登录或首次注册。
     * <ol><li>换取身份</li><li>创建用户</li><li>签发 JWT</li></ol>
     *
     * @param req 微信登录请求
     * @return 当前用户的登录信息
     * @throws BizException 登录码无法换取有效身份时抛出
     */
    public LoginResponse login(LoginRequest req) {
        String openid = resolveOpenid(req.getCode());
        if (!StringUtils.hasText(openid)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "无效的 code，获取 openid 失败");
        }

        User user = userMapper.selectByOpenid(openid);
        // 首次微信登录才创建账户，后续登录沿用既有资料。
        boolean created = user == null;
        if (created) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname("用户" + openid.substring(Math.max(0, openid.length() - 6)));
            user.setAvatar(AvatarCatalog.DEFAULT_AVATAR_KEY);
            user.setCreatedAt(LocalDateTime.now());
            userMapper.insert(user);
            log.info("user_account user_id={} status=CREATED", user.getId());
        }

        String token = jwtUtil.generateToken(user.getId());
        log.info("user_login user_id={} created={}", user.getId(), created);
        return new LoginResponse(token, user.getId(), user.getNickname(), user.getAvatar());
    }

    /**
     * 获取当前用户资料。
     * <ol><li>确认登录</li><li>转换资料</li></ol>
     *
     * @return 当前用户资料
     * @throws BizException 登录态已失效时抛出
     */
    public UserProfileDto getProfile() {
        return toDto(currentUser());
    }

    /**
     * 更新当前用户的昵称和内置头像。
     * <ol><li>确认登录</li><li>校验头像</li><li>返回资料</li></ol>
     *
     * @param req 待更新的资料字段
     * @return 更新后的用户资料
     * @throws BizException 头像不在内置目录或登录态失效时抛出
     */
    public UserProfileDto updateProfile(UpdateProfileRequest req) {
        Long userId = UserContext.currentUserId();
        User user = currentUser();

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        boolean hasUpdate = false;

        if (StringUtils.hasText(req.getNickname())) {
            wrapper.set(User::getNickname, req.getNickname());
            hasUpdate = true;
        }
        if (req.getAvatar() != null) {
            if (!AvatarCatalog.isSupported(req.getAvatar())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "头像类型无效");
            }
            wrapper.set(User::getAvatar, req.getAvatar());
            hasUpdate = true;
        }

        if (hasUpdate) {
            userMapper.update(null, wrapper);
            user = userMapper.selectById(userId);
            log.info("user_profile user_id={} status=UPDATED", userId);
        }

        return toDto(user);
    }

    /**
     * 用微信临时登录码换取 OpenID。
     * <ol><li>请求微信</li><li>读取身份</li><li>屏蔽失败</li></ol>
     *
     * @param code 微信临时登录码
     * @return 有效 OpenID；换取失败时为 {@code null}
     */
    private String resolveOpenid(String code) {
        long startedAt = System.nanoTime();
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://api.weixin.qq.com/sns/jscode2session")
                    .queryParam("appid", appid)
                    .queryParam("secret", wechatSecret)
                    .queryParam("js_code", code)
                    .queryParam("grant_type", "authorization_code")
                    .build()
                    .encode()
                    .toUriString();
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);
            if (!StringUtils.hasText(response)) {
                log.warn("wechat_code2session status=EMPTY_RESPONSE duration_ms={}", elapsedMillis(startedAt));
                return null;
            }
            JsonNode result = objectMapper.readTree(response);
            if (result.hasNonNull("openid")) {
                log.info("wechat_code2session status=SUCCEEDED duration_ms={}", elapsedMillis(startedAt));
                return result.get("openid").asText();
            }
            log.warn("wechat_code2session status=REJECTED error_code={} duration_ms={}",
                    result.path("errcode").asText("unknown"), elapsedMillis(startedAt));
            return null;
        } catch (Exception e) {
            log.error("wechat_code2session status=FAILED duration_ms={} failure_category={}",
                    elapsedMillis(startedAt), e.getClass().getSimpleName(), SafeLogException.from(e));
            return null;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private UserProfileDto toDto(User user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setUserId(user.getId());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        dto.setPhone(user.getPhone());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }

    private User currentUser() {
        Long userId = UserContext.currentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "登录状态已失效，请重新登录");
        }
        return user;
    }
}
