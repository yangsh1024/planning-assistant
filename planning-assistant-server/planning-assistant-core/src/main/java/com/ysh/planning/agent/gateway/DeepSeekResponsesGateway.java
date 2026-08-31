package com.ysh.planning.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.agent.dto.AgentActionDto;
import com.ysh.planning.agent.tool.AgentToolCatalog;
import com.ysh.planning.agent.tool.AgentToolExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * 将账本对话转发给模型服务，并在服务端闭环处理工具调用。
 * 浏览器只接收回答片段和确认卡片，模型工具协议不暴露到客户端。
 */
@Component
@RequiredArgsConstructor
public class DeepSeekResponsesGateway implements AgentModelGateway {
    private final ObjectMapper objectMapper;
    private final AgentToolExecutor toolExecutor;
    @Value("${deepseek.api-key:}")
    private String apiKey;
    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;
    @Value("${deepseek.model:deepseek-v4-flash}")
    private String model;
    @Value("${agent.timeout-seconds:60}")
    private int timeoutSeconds;
    @Value("${agent.max-output-tokens:4096}")
    private int maxOutputTokens;
    private final java.util.concurrent.ConcurrentMap<Thread, HttpURLConnection> activeConnections = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 终止指定对话工作线程的上游请求。
     * <ol><li>断开连接</li><li>中断线程</li></ol>
     *
     * @param worker 承载模型请求的工作线程
     */
    @Override
    public void cancel(Thread worker) {
        HttpURLConnection connection = activeConnections.remove(worker);
        if (connection != null) connection.disconnect();
        worker.interrupt();
    }

    /**
     * 流式获取模型回答并处理其工具请求。
     * <ol><li>调用模型</li><li>执行工具</li><li>返回确认</li></ol>
     *
     * @param messages        已裁剪的会话上下文
     * @param thinkingEnabled 是否启用低强度推理
     * @param sessionId       当前会话标识
     * @param onDelta         可公开的回答片段回调
     * @param onAction        待用户确认操作的回调
     * @return 本轮模型与 token 用量信息
     * @throws BizException 模型未配置、超时、不可用或工具调用过多时抛出
     */
    @Override
    public GatewayResult stream(List<AgentPromptMessage> messages, boolean thinkingEnabled, String sessionId,
                                Consumer<String> onDelta, Consumer<AgentActionDto> onAction) {
        if (apiKey.isBlank()) throw new BizException(503, "Agent 服务尚未配置");
        List<Object> input = new ArrayList<>(messages.stream().map(m -> (Object) Map.of("role", m.role(), "content", m.content())).toList());
        int inputTokens = 0;
        int outputTokens = 0;
        String responseModel = model;
        long deadlineNanos = System.nanoTime() + timeoutSeconds * 1_000_000_000L;
        // 限制工具往返次数，避免模型在单轮请求中无限循环。
        for (int round = 0; round < 5; round++) {
            if (System.nanoTime() >= deadlineNanos || Thread.currentThread().isInterrupted())
                throw new BizException(503, "Agent 请求超时，请重试");
            int remainingSeconds = (int) Math.max(1, (deadlineNanos - System.nanoTime()) / 1_000_000_000L);
            CallResult call = call(input, thinkingEnabled, onDelta, remainingSeconds, deadlineNanos);
            inputTokens += call.inputTokens();
            outputTokens += call.outputTokens();
            if (call.model() != null && !call.model().isBlank()) responseModel = call.model();
            List<JsonNode> functionCalls = call.outputItems().stream().filter(i -> "function_call".equals(i.path("type").asText())).toList();
            if (functionCalls.isEmpty()) return new GatewayResult(responseModel, inputTokens, outputTokens);
            call.outputItems().forEach(item -> input.add(objectMapper.convertValue(item, Map.class)));
            for (JsonNode functionCall : functionCalls) {
                AgentToolExecutor.ToolResult result;
                try {
                    result = toolExecutor.execute(sessionId, functionCall.path("name").asText(), functionCall.path("arguments").asText("{}"));
                } catch (BizException e) {
                    String error = objectMapper.createObjectNode().put("tool_error", e.getMessage()).toString();
                    input.add(Map.of("type", "function_call_output", "call_id", functionCall.path("call_id").asText(), "output", error));
                    continue;
                }
                // 写入意图到达确认阶段即停止本轮，不能让模型继续假定操作已经生效。
                if (result.action() != null) {
                    onAction.accept(result.action());
                    return new GatewayResult(responseModel, inputTokens, outputTokens);
                }
                input.add(Map.of("type", "function_call_output", "call_id", functionCall.path("call_id").asText(), "output", result.output()));
            }
        }
        throw new BizException(503, "Agent 工具调用次数过多");
    }

    private CallResult call(List<Object> input, boolean thinkingEnabled, Consumer<String> onDelta, int remainingSeconds, long deadlineNanos) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(baseUrl.replaceAll("/$", "") + "/responses").toURL().openConnection();
            activeConnections.put(Thread.currentThread(), connection);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(remainingSeconds * 1000);
            connection.setReadTimeout(remainingSeconds * 1000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("stream", true);
            payload.put("instructions", "你是小猫月度预算账本助手。查询必须使用只读工具；写入只能调用 prepare_* 工具创建确认卡片。不得声称已执行未确认操作。");
            payload.put("reasoning", Map.of("effort", AgentThinkingPolicy.effort(thinkingEnabled)));
            payload.put("input", input);
            payload.put("tools", AgentToolCatalog.definitions());
            payload.put("tool_choice", "auto");
            payload.put("max_output_tokens", maxOutputTokens);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(objectMapper.writeValueAsBytes(payload));
            }
            int status = connection.getResponseCode();
            if (status == 429) throw new BizException(429, "Agent 服务繁忙，请稍后重试");
            if (status >= 400) throw new BizException(503, "Agent 服务暂不可用");
            List<JsonNode> outputItems = new ArrayList<>();
            int inputTokens = 0;
            int outputTokens = 0;
            String responseModel = model;
            boolean completed = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (System.nanoTime() >= deadlineNanos || Thread.currentThread().isInterrupted())
                        throw new BizException(503, "Agent 请求超时，请重试");
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;
                    JsonNode event = objectMapper.readTree(data);
                    String type = event.path("type").asText();
                    if ("response.output_text.delta".equals(type)) {
                        onDelta.accept(event.path("delta").asText());
                    } else if ("response.output_item.done".equals(type) && event.has("item")) {
                        outputItems.add(event.get("item"));
                    } else if ("response.completed".equals(type)) {
                        JsonNode response = event.path("response");
                        responseModel = response.path("model").asText(model);
                        inputTokens = response.path("usage").path("input_tokens").asInt(0);
                        outputTokens = response.path("usage").path("output_tokens").asInt(0);
                        completed = true;
                    } else if ("response.failed".equals(type) || "response.incomplete".equals(type)) {
                        throw new BizException(503, "Agent 服务未完成响应");
                    }
                }
            }
            if (!completed) throw new BizException(503, "Agent 连接中断，请重试");
            return new CallResult(outputItems, responseModel, inputTokens, outputTokens);
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(503, "Agent 网络请求失败，请重试");
        } finally {
            if (connection != null) {
                activeConnections.remove(Thread.currentThread(), connection);
                connection.disconnect();
            }
        }
    }

    private record CallResult(List<JsonNode> outputItems, String model, int inputTokens, int outputTokens) {
    }
}
