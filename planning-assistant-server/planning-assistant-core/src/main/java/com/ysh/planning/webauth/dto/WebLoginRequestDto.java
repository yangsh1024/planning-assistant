package com.ysh.planning.webauth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WebLoginRequestDto {
    private String requestId;
    private String browserProof;
    private String mode;
    private String loginCode;
    private String fixedQrCodeUrl;
    private LocalDateTime expiresAt;
}
