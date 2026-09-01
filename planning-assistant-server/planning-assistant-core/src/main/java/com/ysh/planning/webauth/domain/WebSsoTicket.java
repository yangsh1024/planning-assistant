package com.ysh.planning.webauth.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保存小程序复制到浏览器的一次性登录票据摘要及消费状态。
 * 原始票据不落库，消费标记确保同一链接只能交换一次 Web 会话。
 */
@Data
@TableName("t_web_sso_ticket")
public class WebSsoTicket {
    @TableId
    private String id;
    private Long userId;
    private String ticketHash;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private LocalDateTime createdAt;
}
