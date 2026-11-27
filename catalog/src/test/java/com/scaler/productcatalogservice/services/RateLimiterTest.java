package com.scaler.productcatalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RateLimiterTest {

    private static final String KEY = "shopnest:rate:search:user:asha";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RateLimiter limiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        limiter = new RateLimiter(redis);
    }

    @Test
    void secondsToWait_theFirstRequest_startsTheMinuteAndLetsItThrough() {
        when(values.increment(KEY)).thenReturn(1L);

        assertThat(limiter.secondsToWait("user:asha")).isZero();

        verify(redis).expire(KEY, Duration.ofMinutes(1));
    }

    @Test
    void secondsToWait_theFifthRequest_stillGoesThroughAndDoesNotRestartTheMinute() {
        when(values.increment(KEY)).thenReturn(5L);

        assertThat(limiter.secondsToWait("user:asha")).isZero();

        verify(redis, never()).expire(KEY, Duration.ofMinutes(1));
    }

    @Test
    void secondsToWait_theSixthRequest_getsTheSecondsThatAreLeft() {
        when(values.increment(KEY)).thenReturn(6L);
        when(redis.getExpire(KEY)).thenReturn(41L);

        assertThat(limiter.secondsToWait("user:asha")).isEqualTo(41);
    }

    @Test
    void secondsToWait_theKeyHasNoTimeLeft_saysOneSecond() {
        when(values.increment(KEY)).thenReturn(9L);
        when(redis.getExpire(KEY)).thenReturn(-1L);

        assertThat(limiter.secondsToWait("user:asha")).isEqualTo(1);
    }

    @Test
    void secondsToWait_redisIsDown_letsEveryoneThrough() {
        when(values.increment(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(limiter.secondsToWait("user:asha")).isZero();
    }

    @Test
    void secondsToWait_twoCallers_haveTwoCounters() {
        when(values.increment("shopnest:rate:search:ip:10.0.0.7")).thenReturn(1L);

        limiter.secondsToWait("ip:10.0.0.7");

        verify(values).increment("shopnest:rate:search:ip:10.0.0.7");
        verify(values, never()).increment(KEY);
    }
}
