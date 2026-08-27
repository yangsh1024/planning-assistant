package com.ysh.planning.agent.policy;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRateLimiterTest {
    @Test
    void resetsCounterAfterOneMinute() {
        AgentRateLimiter limiter = new AgentRateLimiter(2);
        Instant start = Instant.parse("2026-08-27T00:00:00Z");
        assertTrue(limiter.tryAcquire(7L, start));
        assertTrue(limiter.tryAcquire(7L, start.plusSeconds(10)));
        assertFalse(limiter.tryAcquire(7L, start.plusSeconds(20)));
        assertTrue(limiter.tryAcquire(7L, start.plusSeconds(61)));
    }
}
