package com.ysh.planning.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "code 不能为空")
    private String code;
}
