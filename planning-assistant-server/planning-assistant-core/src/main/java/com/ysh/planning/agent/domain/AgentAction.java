package com.ysh.planning.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_action_audit")
public class AgentAction {
    @TableId private String id;
    private String sessionId;
    private Long userId;
    private String actionType;
    private String summary;
    private String payloadJson;
    private String targetFingerprint;
    private String idempotencyKey;
    private String status;
    private LocalDateTime expiresAt;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
