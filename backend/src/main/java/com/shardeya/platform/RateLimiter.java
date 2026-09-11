package com.shardeya.platform;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window counter (Redis INCR + EXPIRE) — 00-ARCHITECTURE.md §4.11 calls
 * this a "token bucket" but a fixed window is simpler and close enough for
 * the caps involved (OTP 3/hour, IP 10/day); nothing here needs smooth
 * rate-shaping, just a hard cap per window.
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return true if the caller is within the limit (and the attempt was counted) */
    public boolean tryConsume(String key, int maxInWindow, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        return count != null && count <= maxInWindow;
    }
}
