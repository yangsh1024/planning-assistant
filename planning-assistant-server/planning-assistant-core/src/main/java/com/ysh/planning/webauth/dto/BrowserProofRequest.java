package com.ysh.planning.webauth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BrowserProofRequest { @NotBlank private String browserProof; }
