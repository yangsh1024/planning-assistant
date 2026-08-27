package com.ysh.planning.webauth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WebLoginRequestDto {
    private String requestId;
    private String browserProof;
    private String mode;
    private String fallbackCode;
    private String fixedQrCodeUrl;
    private String qrCodeUrl;
    private LocalDateTime expiresAt;
}
