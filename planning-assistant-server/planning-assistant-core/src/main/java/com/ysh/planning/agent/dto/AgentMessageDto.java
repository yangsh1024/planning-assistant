package com.ysh.planning.agent.dto;
import lombok.Data; import java.time.LocalDateTime;
@Data public class AgentMessageDto { private String messageId; private String role; private String content; private AgentActionDto action; private String status; private LocalDateTime createdAt; }
