package com.ysh.planning.agent.gateway;
import java.util.List;
import java.util.function.Consumer;
import com.ysh.planning.agent.dto.AgentActionDto;
public interface AgentModelGateway {
    GatewayResult stream(List<AgentPromptMessage> messages, boolean thinkingEnabled, String sessionId,
                         Consumer<String> onDelta, Consumer<AgentActionDto> onAction);
    default void cancel(Thread worker) { worker.interrupt(); }
    record AgentPromptMessage(String role, String content) { }
    record GatewayResult(String model, int inputTokens, int outputTokens) { }
}
