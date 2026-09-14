package com.maimai.common;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** 单机固定窗口限流；定期回收过期键，达到容量时拒绝新键，防止内存持续增长。 */
@Component
public class SimpleRateLimiter {
    private static final int MAX_KEYS = 20_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int capacity;
    private long nextSweep;
    private static class Window {
        final long expiresAt;
        int count;
        Window(long expiresAt) { this.expiresAt = expiresAt; }
    }
    public SimpleRateLimiter() { this(Clock.systemUTC(), MAX_KEYS); }
    SimpleRateLimiter(Clock clock, int capacity) { this.clock = clock; this.capacity = capacity; }

    public synchronized boolean tryAcquire(String key, int maxPerWindow, long windowSeconds) {
        if (key == null || key.length() > 1024 || maxPerWindow < 1 || windowSeconds < 1) return false;
        long now = clock.millis();
        if (now >= nextSweep) {
            windows.values().removeIf(window -> window.expiresAt <= now);
            nextSweep = now + 60_000;
        }
        Window window = windows.get(key);
        if (window == null || window.expiresAt <= now) {
            if (window == null && windows.size() >= capacity) {
                windows.values().removeIf(candidate -> candidate.expiresAt <= now);
                if (windows.size() >= capacity) return false;
            }
            window = new Window(Math.addExact(now, Math.multiplyExact(windowSeconds, 1000)));
            windows.put(key, window);
        }
        if (window.count >= maxPerWindow) return false;
        window.count++;
        return true;
    }
    public void require(String key, int maxPerWindow, long windowSeconds, String message) {
        if (!tryAcquire(key, maxPerWindow, windowSeconds)) throw BizException.tooMany(message);
    }
}
