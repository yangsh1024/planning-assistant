package com.ysh.planning.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
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

    public LoginResponse login(LoginRequest req) {
        String openid = resolveOpenid(req.getCode());
        if (!StringUtils.hasText(openid)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "无效的 code，获取 openid 失败");
        }

        User user = userMapper.selectByOpenid(openid);
        // 首次微信登录才创建账户，后续登录沿用既有资料。
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname("用户" + openid.substring(Math.max(0, openid.length() - 6)));
            user.setAvatar(AvatarCatalog.DEFAULT_AVATAR_KEY);
            user.setCreatedAt(LocalDateTime.now());
            userMapper.insert(user);
        }

        String token = jwtUtil.generateToken(user.getId());
        return new LoginResponse(token, user.getId(), user.getNickname(), user.getAvatar());
    }

    public UserProfileDto getProfile() {
        return toDto(currentUser());
    }

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
        }

        return toDto(user);
    }

    private String resolveOpenid(String code) {
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
                log.warn("WeChat code2session returned an empty response");
                return null;
            }
            JsonNode result = objectMapper.readTree(response);
            if (result.hasNonNull("openid")) {
                return result.get("openid").asText();
            }
            log.warn("WeChat code2session failed: errcode={}, errmsg={}",
                    result.path("errcode").asText("unknown"),
                    result.path("errmsg").asText("unknown"));
            return null;
        } catch (Exception e) {
            log.error("WeChat code2session error", e);
            return null;
        }
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
