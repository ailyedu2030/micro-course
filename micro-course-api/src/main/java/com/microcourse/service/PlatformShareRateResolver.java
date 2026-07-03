package com.microcourse.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 平台分账率解析器 - 集中处理 platform_share_config 表的读取
 *
 * 修复 P0-1: 之前 TeacherServiceImpl 和 AdminStatsServiceImpl 都硬编码分账率
 *           admin 改配置后实际不生效
 * 修复 P0-2: 之前 GOLD 等级前后端不一致 (前端 22% / 后端 25%)
 * 修复 P0-3: 之前 admin 营收看板硬编码 30%
 *
 * 解决: 统一从这里读。如果 config 表没有该 key,使用硬编码默认值兜底。
 */
@Service
public class PlatformShareRateResolver {

    private final PlatformShareConfigService configService;

    public PlatformShareRateResolver(PlatformShareConfigService configService) {
        this.configService = configService;
    }

    /**
     * 根据教师等级获取平台分账率(%)
     * 优先级: platform_share_config 表 → 硬编码默认值
     */
    public BigDecimal getRateByTier(String tier) {
        String key = "TIER_" + tier + "_RATE";
        Optional<BigDecimal> fromConfig = configService.findByKey(key)
                .map(dto -> new BigDecimal(dto.getConfigValue()));
        if (fromConfig.isPresent()) {
            return fromConfig.get();
        }
        return getDefaultRate(tier);
    }

    /**
     * 默认分账率(配置文件丢失时的兜底)
     * 与 V111 迁移的 INSERT 值一致
     */
    public BigDecimal getDefaultRate(String tier) {
        switch (tier) {
            case "NEW":      return new BigDecimal("35");
            case "BRONZE":   return new BigDecimal("32");
            case "SILVER":   return new BigDecimal("28");
            case "GOLD":     return new BigDecimal("25");
            case "PLATINUM": return new BigDecimal("20");
            default:         return new BigDecimal("30");
        }
    }

    /**
     * 获取默认全局分账率(用于没有教师等级时的场景)
     */
    public BigDecimal getDefaultGlobalRate() {
        Optional<BigDecimal> fromConfig = configService.findByKey("DEFAULT_SHARE_RATE")
                .map(dto -> new BigDecimal(dto.getConfigValue()));
        return fromConfig.orElseGet(() -> new BigDecimal("30"));
    }
}
