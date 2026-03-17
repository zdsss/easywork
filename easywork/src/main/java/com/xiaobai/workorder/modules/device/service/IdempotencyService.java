package com.xiaobai.workorder.modules.device.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Idempotency guard for device batch operations.
 *
 * <p>Clients generate a UUID and send it as the {@code Idempotency-Key} request header.
 * The server caches the response in Redis for {@link #TTL}.  Replayed requests with the
 * same key receive the cached response immediately, preventing duplicate operations when
 * the offline queue retries after a network blip.</p>
 *
 * <p>When Redis is unavailable (e.g. integration test profile excludes Redis), the
 * service degrades gracefully: {@link #getCached} always returns {@code null} and
 * {@link #cache} is a no-op.  Idempotency is not enforced but requests still succeed.</p>
 */
@Slf4j
@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String PREFIX = "idempotency:";

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Returns the cached result for the given idempotency key, or {@code null} if not found
     * or if Redis is unavailable.
     */
    public Object getCached(String key) {
        if (redisTemplate == null) return null;
        return redisTemplate.opsForValue().get(PREFIX + key);
    }

    /**
     * Stores the result under the given idempotency key for {@link #TTL}.
     * No-op if Redis is unavailable.
     */
    public void cache(String key, Object result) {
        if (redisTemplate == null) return;
        redisTemplate.opsForValue().set(PREFIX + key, result, TTL);
        log.debug("Cached idempotency result for key={}", key);
    }
}
