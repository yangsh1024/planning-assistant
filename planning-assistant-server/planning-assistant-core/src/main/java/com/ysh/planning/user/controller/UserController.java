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

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(userService.login(req));
    }

    @GetMapping("/profile")
    public Result<UserProfileDto> getProfile() {
        return Result.ok(userService.getProfile());
    }

    @PutMapping("/profile")
    public Result<UserProfileDto> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return Result.ok(userService.updateProfile(req));
    }

}
