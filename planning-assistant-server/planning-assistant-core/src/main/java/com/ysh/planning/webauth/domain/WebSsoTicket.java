package com.ysh.planning.webauth.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
