package com.microcourse.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置（Lettuce 客户端 + Jackson 序列化）
 *
 * 依据：
 * - Phase 1 业务逻辑 §2.1：登录失败次数 Redis key = login:lock:{username}
 * - Phase 1 业务逻辑 §2.4：登出黑名单 Redis key = jwt:blacklist:{jti}
 * - 穷举审查 E5 P0-1：缺 spring-boot-starter-data-redis 依赖 + spring.data.redis 配置
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate<String, Object>
     * - key 序列化为 String（可读性优先）
     * - value 序列化为 JSON（Jackson）
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // String 序列化器（key）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Jackson 序列化器（value）
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(buildObjectMapper(), Object.class);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // P2 #30 fix: NON_FINAL with open base type (Object.class) allows arbitrary class
        // deserialization - security risk (e.g., com.sun.rowset.JdbcRowSetImpl RCE payload).
        // Fix: use a restrictive PolymorphicTypeValidator that only allows:
        //   1. JDK built-in types (String, Number, Boolean, byte[])
        //   2. Application DTOs in com.microcourse.dto.* package
        // DTOs are trusted (we control them), but anything outside this whitelist
        // (e.g., org.springframework., com.sun., java.util. arbitrary classes)
        // is denied at deserialization time.
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(String.class)
                .allowIfBaseType(Boolean.class)
                .allowIfBaseType(Integer.class)
                .allowIfBaseType(Long.class)
                .allowIfBaseType(Double.class)
                .allowIfBaseType(Float.class)
                .allowIfBaseType(Number.class)
                .allowIfBaseType(byte[].class)
                .allowIfSubType("com.microcourse.dto.")
                .allowIfSubType("com.microcourse.entity.")
                .allowIfSubType("java.util.ArrayList")
                .allowIfSubType("java.util.HashMap")
                .allowIfSubType("java.util.LinkedList")
                .build();
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }
}
