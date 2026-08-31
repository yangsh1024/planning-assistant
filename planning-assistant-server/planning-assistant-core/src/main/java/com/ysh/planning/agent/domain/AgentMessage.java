package com.ysh.planning.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保存 Agent 会话中的可见消息、生成状态及关联确认操作。
 */
@Data
@TableName("t_agent_message")
public class AgentMessage {
    @TableId
    private String id;
    private String sessionId;
    private Long userId;
    private String role;
    private String content;
    private String actionId;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private String status;
    private LocalDateTime createdAt;
}
