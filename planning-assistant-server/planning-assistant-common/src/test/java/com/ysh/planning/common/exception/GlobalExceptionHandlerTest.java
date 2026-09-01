package com.ysh.planning.common.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void preservesAgentRetryableHttpStatuses() {
        assertThat(handler.handleBiz(new BizException(429, "busy")).getStatusCode().value()).isEqualTo(429);
        assertThat(handler.handleBiz(new BizException(503, "unavailable")).getStatusCode().value()).isEqualTo(503);
        assertThat(handler.handleBiz(new BizException(409, "consumed")).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void doesNotLogRejectedFieldValue() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            BeanPropertyBindingResult result = new BeanPropertyBindingResult(new AmountRequest(), "request");
            result.rejectValue("amount", "invalid", "金额 12.34 不合法");

            handler.handleValidation(new MethodArgumentNotValidException(null, result));

            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("validation_failure")
                            .doesNotContain("12.34"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static final class AmountRequest {
        public String getAmount() {
            return "12.34";
        }
    }
}
