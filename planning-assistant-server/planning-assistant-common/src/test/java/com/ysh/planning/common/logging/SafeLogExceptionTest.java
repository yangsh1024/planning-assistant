package com.ysh.planning.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SafeLogExceptionTest {

    @Test
    void retainsStackTraceWithoutRetainingSensitiveMessageOrCause() throws Exception {
        IllegalStateException source = new IllegalStateException(
                "https://example.test?secret=secret-value&access_token=token-value");
        source.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("ExampleClient", "call", "ExampleClient.java", 42)
        });

        assertThatCode(() -> Class.forName("com.ysh.planning.common.logging.SafeLogException"))
                .doesNotThrowAnyException();
        Class<?> type = Class.forName("com.ysh.planning.common.logging.SafeLogException");
        RuntimeException safe = (RuntimeException) type.getMethod("from", Throwable.class).invoke(null, source);

        assertThat(safe.getMessage()).isEqualTo("IllegalStateException");
        assertThat(safe.getCause()).isNull();
        assertThat(safe.getStackTrace()).containsExactly(source.getStackTrace());
        assertThat(safe.getMessage()).doesNotContain("secret-value", "token-value");
    }
}
