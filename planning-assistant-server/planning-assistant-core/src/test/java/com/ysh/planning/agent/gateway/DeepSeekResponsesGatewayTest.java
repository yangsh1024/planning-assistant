package com.ysh.planning.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ysh.planning.agent.tool.AgentToolExecutor;
import com.ysh.planning.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepSeekResponsesGatewayTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolExecutor executor = mock(AgentToolExecutor.class);
    private final List<JsonNode> requests = new ArrayList<>();
    private HttpServer server;
    private DeepSeekResponsesGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway = new DeepSeekResponsesGateway(objectMapper, executor);
        ReflectionTestUtils.setField(gateway, "apiKey", "test-key");
        ReflectionTestUtils.setField(gateway, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(gateway, "model", "deepseek-v4-flash");
        ReflectionTestUtils.setField(gateway, "timeoutSeconds", 2);
        ReflectionTestUtils.setField(gateway, "maxOutputTokens", 4096);
    }

    @AfterEach
    void tearDown() { if (server != null) server.stop(0); }

    @Test
    void streamsOnlyVisibleTextAndCollectsUsageWithThinkingEnabled() {
        server.createContext("/responses", exchange -> respond(exchange, 200,
                event("response.output_text.delta", "\"delta\":\"\u7ed3\u679c\"") +
                event("response.output_item.done", "\"item\":{\"type\":\"reasoning\",\"content\":\"secret\"}") +
                completed("deepseek-test", 12, 7)));
        server.start();
        StringBuilder visible = new StringBuilder();

        AgentModelGateway.GatewayResult result = gateway.stream(
                List.of(new AgentModelGateway.AgentPromptMessage("user", "\u67e5\u9884\u7b97")), true, "session",
                visible::append, action -> { });

        assertThat(visible).hasToString("\u7ed3\u679c");
        assertThat(result.model()).isEqualTo("deepseek-test");
        assertThat(result.inputTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(7);
        assertThat(requests.getFirst().path("reasoning").path("effort").asText()).isEqualTo("low");
        assertThat(requests.getFirst().path("max_output_tokens").asInt()).isEqualTo(4096);
    }

    @Test
    void sendsReasoningItemBackOnlyInsideToolLoop() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(executor.execute("session", "read_trend", "{}")).thenReturn(new AgentToolExecutor.ToolResult("[]", null));
        server.createContext("/responses", exchange -> {
            if (calls.getAndIncrement() == 0) respond(exchange, 200,
                    event("response.output_item.done", "\"item\":{\"type\":\"reasoning\",\"id\":\"r1\",\"content\":\"secret\"}") +
                    event("response.output_item.done", "\"item\":{\"type\":\"function_call\",\"call_id\":\"c1\",\"name\":\"read_trend\",\"arguments\":\"{}\"}") +
                    completed("deepseek-test", 4, 2));
            else respond(exchange, 200, event("response.output_text.delta", "\"delta\":\"ok\"") + completed("deepseek-test", 6, 3));
        });
        server.start();
        StringBuilder visible = new StringBuilder();

        AgentModelGateway.GatewayResult result = gateway.stream(
                List.of(new AgentModelGateway.AgentPromptMessage("user", "trend")), false, "session",
                visible::append, action -> { });

        assertThat(visible).hasToString("ok");
        assertThat(result.inputTokens()).isEqualTo(10);
        assertThat(result.outputTokens()).isEqualTo(5);
        assertThat(requests.get(1).path("input").toString()).contains("reasoning", "function_call_output");
        assertThat(requests.get(1).path("reasoning").path("effort").asText()).isEqualTo("none");
    }

    @Test
    void returnsInvalidToolArgumentsToModelForCorrection() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(executor.execute("session", "read_expenses", "{\"pageSize\":1000}"))
                .thenThrow(new BizException(400, "pageSize 超出范围"));
        server.createContext("/responses", exchange -> {
            if (calls.getAndIncrement() == 0) respond(exchange, 200,
                    event("response.output_item.done", "\"item\":{\"type\":\"function_call\",\"call_id\":\"c1\",\"name\":\"read_expenses\",\"arguments\":\"{\\\"pageSize\\\":1000}\"}") +
                    completed("deepseek-test", 4, 2));
            else respond(exchange, 200, event("response.output_text.delta", "\"delta\":\"请补充月份\"") + completed("deepseek-test", 6, 3));
        });
        server.start();

        gateway.stream(List.of(new AgentModelGateway.AgentPromptMessage("user", "查账")), false, "session", ignored -> { }, ignored -> { });

        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).path("input").toString()).contains("tool_error", "pageSize 超出范围");
    }

    @Test
    void mapsRateLimitAndInterruptedStreamToRetryableServiceErrors() {
        server.createContext("/responses", exchange -> respond(exchange, 429, "{}"));
        server.start();
        assertThatThrownBy(() -> gateway.stream(List.of(), false, "session", ignored -> { }, ignored -> { }))
                .isInstanceOf(BizException.class).hasMessageContaining("\u7e41忙");
        server.stop(0);

        requests.clear();
        try { server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        catch (IOException e) { throw new AssertionError(e); }
        ReflectionTestUtils.setField(gateway, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        server.createContext("/responses", exchange -> respond(exchange, 200, event("response.output_text.delta", "\"delta\":\"partial\"")));
        server.start();
        assertThatThrownBy(() -> gateway.stream(List.of(), false, "session", ignored -> { }, ignored -> { }))
                .isInstanceOf(BizException.class).hasMessageContaining("\u4e2d断");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        requests.add(objectMapper.readTree(exchange.getRequestBody()));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String event(String type, String fields) { return "data: {\"type\":\"" + type + "\"," + fields + "}\n\n"; }
    private String completed(String model, int input, int output) {
        return event("response.completed", "\"response\":{\"model\":\"" + model + "\",\"usage\":{\"input_tokens\":" + input + ",\"output_tokens\":" + output + "}}");
    }
}
