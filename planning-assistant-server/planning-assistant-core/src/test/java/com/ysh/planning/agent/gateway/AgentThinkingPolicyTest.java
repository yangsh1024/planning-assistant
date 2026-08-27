package com.ysh.planning.agent.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentThinkingPolicyTest {
    @Test
    void mapsDisabledThinkingToNone() {
        assertEquals("none", AgentThinkingPolicy.effort(false));
    }

    @Test
    void mapsEnabledThinkingToLow() {
        assertEquals("low", AgentThinkingPolicy.effort(true));
    }
}
