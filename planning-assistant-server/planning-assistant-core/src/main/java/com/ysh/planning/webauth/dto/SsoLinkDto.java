package com.ysh.planning.webauth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SsoLinkDto {
    private String loginUrl;
    private LocalDateTime expiresAt;
}
