package com.scaler.productcatalogservice.services;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RateLimiter {

    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    @Value("${search.lab-direct:false}")
    private boolean labDirect;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // 0 means "go on". Any other number is how many seconds the caller must wait.
    public long secondsToWait(String who) {
        if (labDirect) {
            return 0;
        }
        String key = "shopnest:rate:search:" + who;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == 1) {
                redis.expire(key, WINDOW);
            }
            return count <= LIMIT ? 0 : Math.max(1, redis.getExpire(key));
        } catch (DataAccessException e) {
            log.warn("Redis did not answer, so there is no limit for now: {}", e.getMessage());
            return 0;
        }
    }
}
