package com.ysh.planning.webauth.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FallbackCodeRequest { @Pattern(regexp = "\\d{6}") private String fallbackCode; }
