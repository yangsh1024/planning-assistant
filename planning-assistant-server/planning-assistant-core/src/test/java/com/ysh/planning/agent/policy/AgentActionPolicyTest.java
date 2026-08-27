package com.ysh.planning.agent.policy;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActionPolicyTest {
    @Test
    void onlyPendingUnexpiredActionsCanBeConfirmed() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 12, 0);
        assertTrue(AgentActionPolicy.canConfirm("PENDING_CONFIRMATION", now.plusMinutes(1), now));
        assertFalse(AgentActionPolicy.canConfirm("EXECUTED", now.plusMinutes(1), now));
        assertFalse(AgentActionPolicy.canConfirm("PENDING_CONFIRMATION", now.minusSeconds(1), now));
    }
}
