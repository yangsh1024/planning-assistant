package com.ysh.planning.agent.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class AgentChatRequest { private String sessionId; @NotBlank @Size(max = 2000) private String message; private boolean thinkingEnabled; private String retryUserMessageId; }
