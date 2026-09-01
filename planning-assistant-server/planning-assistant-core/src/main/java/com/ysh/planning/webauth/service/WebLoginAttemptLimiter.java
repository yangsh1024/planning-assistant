package com.ysh.planning.webauth.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限制单个小程序用户猜测网页登录码的频率。
 */
@Component
public class WebLoginAttemptLimiter {
    private static final int ATTEMPTS_PER_MINUTE = 10;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(Long userId, Instant now) {
        Instant minute = now.truncatedTo(ChronoUnit.MINUTES);
        Window updated = windows.compute(userId, (ignored, current) ->
                current == null || !current.minute.equals(minute) ? new Window(minute, 1) : new Window(minute, current.count + 1));
        return updated.count <= ATTEMPTS_PER_MINUTE;
    }

    private record Window(Instant minute, int count) {
    }
}
