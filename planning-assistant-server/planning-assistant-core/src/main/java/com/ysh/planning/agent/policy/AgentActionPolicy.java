package com.ysh.planning.agent.policy;

import java.time.LocalDateTime;

public final class AgentActionPolicy {
    private AgentActionPolicy() { }
    public static boolean canConfirm(String status, LocalDateTime expiresAt, LocalDateTime now) {
        return "PENDING_CONFIRMATION".equals(status) && expiresAt != null && expiresAt.isAfter(now);
    }
}
