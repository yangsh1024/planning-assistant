package com.ysh.planning.agent.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按用户限制 Agent 消息发送频率。
 * 限流窗口只保护模型服务容量，不影响其他用户的对话请求。
 */
@Component
public class AgentRateLimiter {
    private final int limit;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    /**
     * 初始化每用户的分钟内消息上限。
     * <ol><li>读取配置</li><li>保证下限</li></ol>
     *
     * @param limit 配置的分钟内请求数
     */
    public AgentRateLimiter(@Value("${agent.per-user-minute-limit:10}") int limit) {
        this.limit = Math.max(1, limit);
    }

    /**
     * 尝试占用当前用户的一次消息配额。
     * <ol><li>定位窗口</li><li>累计次数</li><li>判断额度</li></ol>
     *
     * @param userId 当前用户标识
     * @param now 当前时间
     * @return 未超过当前窗口限额时为 {@code true}
     */
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
