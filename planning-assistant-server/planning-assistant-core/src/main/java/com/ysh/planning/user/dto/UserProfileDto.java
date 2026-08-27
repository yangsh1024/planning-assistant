package com.ysh.planning.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileDto {

    private Long userId;
    private String nickname;
    private String avatar;
    private String phone;
    private LocalDateTime createdAt;
}
