package com.microcourse.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Spring Cache 配置(Phase 21 — RES-NEW-5 修复:热点数据 Redis 缓存)
 *
 * - 默认 TTL 5 分钟,热点可单独覆盖
 * - Key 序列化:String
 * - Value 序列化:JSON(限定 com.microcourse 包,避免 Jackson 反序列化攻击)
 * - 空值不缓存
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // RES-NEW-6 修复:限定多态类型到 com.microcourse 包,防止 Redis 反序列化攻击
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.microcourse.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.time.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .disableCachingNullValues()
                .prefixCacheNameWith("mc:");

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("adminSettingsList", defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("adminSettingsByKey", defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("banners", defaultConfig.entryTtl(Duration.ofMinutes(15)))
                .withCacheConfiguration("categories", defaultConfig.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("academicOverview", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }
}