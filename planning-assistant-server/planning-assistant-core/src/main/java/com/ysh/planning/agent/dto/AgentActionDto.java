package com.ysh.planning.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentActionDto {
    private String actionId;
    private String type;
    private String summary;
    private JsonNode payload;
    private JsonNode result;
    private String status;
    private LocalDateTime expiresAt;
}
