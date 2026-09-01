package com.ysh.planning.user.controller;

import com.ysh.planning.common.response.Result;
import com.ysh.planning.user.dto.LoginRequest;
import com.ysh.planning.user.dto.LoginResponse;
import com.ysh.planning.user.dto.UpdateProfileRequest;
import com.ysh.planning.user.dto.UserProfileDto;
import com.ysh.planning.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供小程序登录和当前用户资料管理接口。 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 使用微信登录凭证建立或获取本地用户会话。 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(userService.login(req));
    }

    /** 读取当前登录用户的资料。 */
    @GetMapping("/profile")
    public Result<UserProfileDto> getProfile() {
        return Result.ok(userService.getProfile());
    }

    /** 更新当前登录用户的资料。 */
    @PutMapping("/profile")
    public Result<UserProfileDto> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return Result.ok(userService.updateProfile(req));
    }

}
