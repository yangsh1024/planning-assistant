package com.ysh.planning.webauth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SsoExchangeRequest { @NotBlank private String ticket; }
