package com.scaler.productcatalogservice.services;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// Every Redis call of the meaning search is here. If Redis fails, we log it and go on without it.
@Slf4j
@Component
public class QueryVectorStore {

    // The numbers of a sentence never change, so a saved copy is good for a day.
    private static final Duration KEEP_FOR = Duration.ofHours(24);
    private static final Duration LOCK_FOR = Duration.ofSeconds(10);

    private final StringRedisTemplate redis;

    public QueryVectorStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String get(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.warn("Redis did not answer: {}", e.getMessage());
            return null;
        }
    }

    public void save(String key, String numbers) {
        try {
            redis.opsForValue().set(key, numbers, KEEP_FOR);
        } catch (DataAccessException e) {
            log.warn("Redis did not answer: {}", e.getMessage());
        }
    }

    // true = I got the lock (or Redis is down, so there is nobody to wait for)
    public boolean tryLock(String key) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key + ":lock", "1", LOCK_FOR));
        } catch (DataAccessException e) {
            log.warn("Redis did not answer: {}", e.getMessage());
            return true;
        }
    }

    public void unlock(String key) {
        try {
            redis.delete(key + ":lock");
        } catch (DataAccessException e) {
            log.warn("Redis did not answer: {}", e.getMessage());
        }
    }

    // Someone else is asking Gemini. Look every 100 ms, for up to 6 seconds.
    // If their lock is gone and there is still no answer, they failed: stop waiting.
    public String waitFor(String key) {
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            String saved = get(key);
            if (saved != null) {
                return saved;
            }
            if (!isLocked(key)) {
                return null;
            }
        }
        return null;
    }

    private boolean isLocked(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key + ":lock"));
        } catch (DataAccessException e) {
            log.warn("Redis did not answer: {}", e.getMessage());
            return false;
        }
    }
}
