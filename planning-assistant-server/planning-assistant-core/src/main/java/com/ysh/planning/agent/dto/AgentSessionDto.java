package com.ysh.planning.agent.dto;
import lombok.Data; import java.time.LocalDateTime;
@Data public class AgentSessionDto { private String sessionId; private String title; private LocalDateTime updatedAt; }
