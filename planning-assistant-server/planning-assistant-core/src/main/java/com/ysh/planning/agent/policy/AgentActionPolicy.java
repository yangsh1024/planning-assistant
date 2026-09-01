package com.ysh.planning.agent.policy;

import java.time.LocalDateTime;

/**
 * 统一判断 Agent 写操作是否仍可确认。
 * 只有待确认且未过期的动作可以进入实际账本写入阶段。
 */
public final class AgentActionPolicy {
    private AgentActionPolicy() {
    }

    /**
     * 判断动作是否满足确认前提。
     * <ol><li>核对状态</li><li>核对期限</li></ol>
     *
     * @param status 动作当前状态
     * @param expiresAt 动作确认期限
     * @param now 当前时间
     * @return 动作可确认时为 {@code true}
     */
    public static boolean canConfirm(String status, LocalDateTime expiresAt, LocalDateTime now) {
        return "PENDING_CONFIRMATION".equals(status) && expiresAt != null && expiresAt.isAfter(now);
    }
}
