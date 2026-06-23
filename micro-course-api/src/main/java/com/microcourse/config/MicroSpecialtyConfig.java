package com.microcourse.config;

import com.microcourse.service.impl.MicroSpecialtyQualityScoreServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Phase 14 — 微专业质量分服务配置
 *
 * <p>将 StringRedisTemplate 适配为 MicroSpecialtyQualityScoreService.Cache 接口，
 * 便于测试 mock 与生产解耦。
 *
 * @author Phase14-Development-Team
 * @since 2026-06-23
 */
@Configuration
public class MicroSpecialtyConfig {

    @Bean
    public MicroSpecialtyQualityScoreServiceImpl.Cache microSpecialtyQualityScoreCache(StringRedisTemplate stringRedisTemplate) {
        return new MicroSpecialtyQualityScoreServiceImpl.RedisStringCache(stringRedisTemplate);
    }
}
