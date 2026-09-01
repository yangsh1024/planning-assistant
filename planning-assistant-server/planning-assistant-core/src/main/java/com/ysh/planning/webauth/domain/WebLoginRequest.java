package com.ysh.planning.webauth.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保存浏览器登录的小程序确认状态与短时校验凭据摘要。
 */
@Data
@TableName("t_web_login_request")
public class WebLoginRequest {
    @TableId
    private String id;
    private Long userId;
    private String browserProofHash;
    @TableField("fallback_code_hash")
    private String loginCodeHash;
    private String deviceLabel;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
