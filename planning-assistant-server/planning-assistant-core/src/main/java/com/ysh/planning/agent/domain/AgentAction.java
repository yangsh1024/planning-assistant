package com.ysh.planning.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保存 Agent 写意图从待确认到最终执行的审计状态。
 * 目标指纹与幂等键用于阻止旧确认覆盖新账本数据或重复执行。
 */
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
