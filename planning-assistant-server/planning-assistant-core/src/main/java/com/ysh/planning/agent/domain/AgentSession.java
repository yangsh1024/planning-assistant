package com.ysh.planning.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("t_agent_session")
public class AgentSession { @TableId private String id; private Long userId; private String title; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
