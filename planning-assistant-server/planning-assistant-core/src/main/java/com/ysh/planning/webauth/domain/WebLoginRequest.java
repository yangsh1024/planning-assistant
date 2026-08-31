package com.ysh.planning.webauth.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_web_login_request")
/** 保存浏览器登录的小程序确认状态与短时校验凭据摘要。 */
public class WebLoginRequest {
    @TableId
    private String id;
    private Long userId;
    private String browserProofHash;
    private String fallbackCodeHash;
    private String deviceLabel;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
