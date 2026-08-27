package com.ysh.planning.agent.gateway;

public final class AgentThinkingPolicy {
    private AgentThinkingPolicy() { }
    public static String effort(boolean enabled) { return enabled ? "low" : "none"; }
}
