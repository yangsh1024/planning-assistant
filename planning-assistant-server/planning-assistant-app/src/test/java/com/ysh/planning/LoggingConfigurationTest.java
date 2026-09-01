package com.ysh.planning;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {

    @Test
    void configuresLogbackForBusinessAndFrameworkLoggers() throws IOException {
        try (InputStream stream = Application.class.getResourceAsStream("/logback-spring.xml")) {
            assertNotNull(stream, "应提供 Logback 配置文件");
            String configuration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(configuration.contains("com.ysh.planning"));
            assertTrue(configuration.contains("org.springframework"));
            assertTrue(configuration.contains("com.baomidou.mybatisplus"));
        }
    }
}
