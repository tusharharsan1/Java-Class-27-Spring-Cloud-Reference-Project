package com.scaler.productcatalogservice.config;

import com.scaler.productcatalogservice.dtos.ProductDto;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

// @EnableCaching turns on @Cacheable and @CacheEvict
@EnableCaching
@Configuration
public class CacheConfig implements CachingConfigurer {

    // The "product" cache: readable JSON (not Java bytes), the key starts with "shopnest:",
    // kept for 10 minutes plus 0 to 59 random seconds, so that keys saved together do not expire together
    @Bean
    public RedisCacheManagerBuilderCustomizer productCache() {
        return builder -> builder.withCacheConfiguration("product",
                RedisCacheConfiguration.defaultCacheConfig()
                        .prefixCacheNameWith("shopnest:")
                        .entryTtl((key, value) -> Duration.ofMinutes(10).plusSeconds(ThreadLocalRandom.current().nextInt(60)))
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(ProductDto.class))));
    }

    // If Redis fails, write one line in the log and go on, as if the cache had no answer. The database answers.
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
