package com.ysh.planning.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void preservesAgentRetryableHttpStatuses() {
        assertThat(handler.handleBiz(new BizException(429, "busy")).getStatusCode().value()).isEqualTo(429);
        assertThat(handler.handleBiz(new BizException(503, "unavailable")).getStatusCode().value()).isEqualTo(503);
        assertThat(handler.handleBiz(new BizException(409, "consumed")).getStatusCode().value()).isEqualTo(409);
    }
}
