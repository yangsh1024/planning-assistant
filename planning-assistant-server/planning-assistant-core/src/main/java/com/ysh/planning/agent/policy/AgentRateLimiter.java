package com.ysh.planning.agent.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentRateLimiter {
    private final int limit;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    public AgentRateLimiter(@Value("${agent.per-user-minute-limit:10}") int limit) {
        this.limit = Math.max(1, limit);
    }

    public boolean tryAcquire(Long userId, Instant now) {
        Window window = windows.compute(userId, (key, current) -> {
            if (current == null || now.isAfter(current.startedAt.plusSeconds(59))) return new Window(now, 1);
            return new Window(current.startedAt, current.count + 1);
        });
        return window.count <= limit;
    }

    private record Window(Instant startedAt, int count) {
    }
}
