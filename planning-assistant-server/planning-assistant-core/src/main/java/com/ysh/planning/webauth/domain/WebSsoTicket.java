package com.ysh.planning.webauth.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_web_sso_ticket")
/** 保存小程序复制到浏览器的一次性登录票据摘要及消费状态。 */
public class WebSsoTicket {
    @TableId
    private String id;
    private Long userId;
    private String ticketHash;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private LocalDateTime createdAt;
}
