package com.ysh.planning.webauth.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 小程序确认页提交的六位网页登录码。 */
@Data
public class LoginCodeRequest {
    @Pattern(regexp = "\\d{6}")
    private String loginCode;
}
