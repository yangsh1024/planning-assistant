package com.ysh.planning.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 20, message = "昵称长度不能超过20")
    private String nickname;

    private String avatar;
}
