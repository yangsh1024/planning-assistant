package com.ysh.planning.webauth.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebLoginAttemptLimiterTest {
    @Test
    void limitsEachUserAndResetsOnNextMinute() {
        WebLoginAttemptLimiter limiter = new WebLoginAttemptLimiter();
        Instant now = Instant.parse("2026-08-27T10:15:00Z");
        for (int attempt = 0; attempt < 10; attempt++) assertThat(limiter.tryAcquire(7L, now)).isTrue();

        assertThat(limiter.tryAcquire(7L, now)).isFalse();
        assertThat(limiter.tryAcquire(8L, now)).isTrue();
        assertThat(limiter.tryAcquire(7L, now.plusSeconds(60))).isTrue();
    }
}
