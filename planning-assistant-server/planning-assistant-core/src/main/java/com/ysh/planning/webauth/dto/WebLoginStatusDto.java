package com.ysh.planning.webauth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WebLoginStatusDto {
    private String requestId;
    private String deviceLabel;
    private String status;
    private LocalDateTime expiresAt;
}
