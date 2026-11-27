package com.scaler.productcatalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

// Every method must survive a Redis that does not answer. That is the whole job of this class.
class QueryVectorStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private QueryVectorStore store;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        store = new QueryVectorStore(redis);
    }

    @AfterEach
    void clearTheInterruptFlag() {
        Thread.interrupted();
    }

    private static RedisConnectionFailureException down() {
        return new RedisConnectionFailureException("down");
    }

    @Test
    void get_returnsWhatRedisHas() {
        when(values.get("k")).thenReturn("the numbers");

        assertThat(store.get("k")).isEqualTo("the numbers");
    }

    @Test
    void get_redisIsDown_returnsNull() {
        when(values.get("k")).thenThrow(down());

        assertThat(store.get("k")).isNull();
    }

    @Test
    void save_keepsTheNumbersForADay() {
        store.save("k", "the numbers");

        verify(values).set("k", "the numbers", Duration.ofHours(24));
    }

    @Test
    void save_redisIsDown_doesNotThrow() {
        doThrow(down()).when(values).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> store.save("k", "the numbers")).doesNotThrowAnyException();
    }

    @Test
    void tryLock_theLockIsFree_returnsTrueAndSetsATimeLimit() {
        when(values.setIfAbsent("k:lock", "1", Duration.ofSeconds(10))).thenReturn(true);

        assertThat(store.tryLock("k")).isTrue();
    }

    @Test
    void tryLock_somebodyHoldsTheLock_returnsFalse() {
        when(values.setIfAbsent("k:lock", "1", Duration.ofSeconds(10))).thenReturn(false);

        assertThat(store.tryLock("k")).isFalse();
    }

    @Test
    void tryLock_redisIsDown_sayYesBecauseNobodyCanBeWaitedFor() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenThrow(down());

        assertThat(store.tryLock("k")).isTrue();
    }

    @Test
    void unlock_deletesTheLockKey() {
        store.unlock("k");

        verify(redis).delete("k:lock");
    }

    @Test
    void unlock_redisIsDown_doesNotThrow() {
        when(redis.delete(anyString())).thenThrow(down());

        assertThatCode(() -> store.unlock("k")).doesNotThrowAnyException();
    }

    @Test
    void waitFor_theAnswerArrivesOnTheSecondLook_returnsIt() {
        when(values.get("k")).thenReturn(null, "the numbers");
        when(redis.hasKey("k:lock")).thenReturn(true);

        assertThat(store.waitFor("k")).isEqualTo("the numbers");
    }

    @Test
    void waitFor_theLockIsGoneAndThereIsNoAnswer_stopsWaiting() {
        when(values.get("k")).thenReturn(null);
        when(redis.hasKey("k:lock")).thenReturn(false);

        long start = System.nanoTime();
        assertThat(store.waitFor("k")).isNull();

        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
    }

    @Test
    void waitFor_redisIsDown_returnsNull() {
        when(values.get("k")).thenThrow(down());
        when(redis.hasKey("k:lock")).thenThrow(down());

        assertThat(store.waitFor("k")).isNull();
    }

    @Test
    void waitFor_theThreadIsInterrupted_returnsNullAndKeepsTheFlag() {
        Thread.currentThread().interrupt();

        assertThat(store.waitFor("k")).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
