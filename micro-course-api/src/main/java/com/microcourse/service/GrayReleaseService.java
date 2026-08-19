package com.microcourse.service;

import com.microcourse.enums.FeatureFlag;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 灰度发布服务 (F10-D2 实现)
 *
 * <p>对接 {@code scripts/gray-release.sh}：
 * <ul>
 *   <li>{@code mc:gray:users} - Redis Set，灰度用户白名单 (userId 集合)
 *   <li>{@code mc:feature:flags} - Redis Hash-like (JSON string)，功能开关 Map&lt;flag, enabled&gt;
 * </ul>
 *
 * <h3>【现象】</h3>
 * 原 {@code gray-release.sh} 仅写入 Redis，但 {@code micro-course-api} 无任何代码读取，
 * 灰度白名单与功能开关实际不改变用户可见行为（已登记 P2 F10-D2）。
 *
 * <h3>【根因】</h3>
 * 后端缺失灰度查询 API，导致运维 SOP 中"灰度发布"流程形式化但不生效。
 *
 * <h3>【修复】</h3>
 * 本服务提供：
 * <ol>
 *   <li>{@link #isGrayUser(Long)} - 判断用户是否在灰度白名单
 *   <li>{@link #isFeatureEnabled(FeatureFlag)} - 查询功能开关
 *   <li>Redis 不可用 / 解析错误 → fail-closed（默认 false，不影响线上行为）
 *   <li>5 秒 TTL 缓存避免每次请求都打 Redis
 * </ol>
 *
 * <h3>【防再发】</h3>
 * 任何新增灰度需求必须：
 * <ol>
 *   <li>在 {@link FeatureFlag} 枚举添加 flag 名（小写 snake_case）
 *   <li>通过本服务查询，禁止直接访问 Redis
 * </ol>
 *
 * @author F10-D2 Phase 9 (2026-08-18)
 */
@Service
public class GrayReleaseService {

    private static final Logger log = LoggerFactory.getLogger(GrayReleaseService.class);

    /** 与 {@code gray-release.sh} 脚本保持同步的 Redis key */
    public static final String FEATURE_FLAG_KEY = "mc:feature:flags";
    public static final String GRAY_USERS_KEY = "mc:gray:users";

    /** Redis 查询缓存 TTL（秒）— 避免每次 HTTP 请求都打 Redis */
    private static final long CACHE_TTL_SECONDS = 5L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 简单本地缓存（5s TTL），避免高频请求打 Redis
    private volatile Map<FeatureFlag, Boolean> flagCache = Collections.emptyMap();
    private volatile Set<Long> grayUserCache = Collections.emptySet();
    private volatile long flagCacheExpireAt = 0L;
    private volatile long grayUserCacheExpireAt = 0L;

    @Autowired
    public GrayReleaseService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询功能开关是否启用（fail-closed）
     *
     * @param flag 功能标识
     * @return true 启用 / false 禁用；Redis 不可用默认 false
     */
    public boolean isFeatureEnabled(FeatureFlag flag) {
        if (flag == null) {
            return false;
        }
        Map<FeatureFlag, Boolean> cache = getFlagCacheSnapshot();
        Boolean cached = cache.get(flag);
        if (cached != null) {
            return cached;
        }
        // 缓存未命中或已过期
        Map<FeatureFlag, Boolean> flags = loadFlagsFromRedis();
        Boolean value = flags.get(flag);
        return value != null && value;
    }

    /**
     * 判断用户是否在灰度白名单
     *
     * @param userId 用户 ID
     * @return true 在白名单 / false 不在；Redis 不可用默认 false
     */
    public boolean isGrayUser(Long userId) {
        if (userId == null) {
            return false;
        }
        Set<Long> cache = getGrayUserCacheSnapshot();
        if (cache.contains(userId)) {
            return true;
        }
        // 缓存未命中或已过期 → 重新加载
        Set<Long> users = loadGrayUsersFromRedis();
        return users.contains(userId);
    }

    /**
     * 主动刷新缓存（灰度变更时由调用方触发）
     */
    public void invalidateCache() {
        flagCacheExpireAt = 0L;
        grayUserCacheExpireAt = 0L;
        log.info("[GrayRelease] cache invalidated");
    }

    /**
     * 直接读取当前 Redis 状态（绕过缓存，用于运维诊断）
     */
    public Map<FeatureFlag, Boolean> loadFlagsFromRedis() {
        try {
            String json = redisTemplate.opsForValue().get(FEATURE_FLAG_KEY);
            if (json == null || json.isBlank()) {
                flagCache = Collections.emptyMap();
                flagCacheExpireAt = System.currentTimeMillis() + CACHE_TTL_SECONDS * 1000;
                return Collections.emptyMap();
            }
            Map<String, Boolean> raw = objectMapper.readValue(json,
                    new TypeReference<Map<String, Boolean>>() {});
            Map<FeatureFlag, Boolean> result = new HashMap<>();
            for (Map.Entry<String, Boolean> e : raw.entrySet()) {
                try {
                    FeatureFlag flag = FeatureFlag.valueOf(e.getKey().toUpperCase());
                    result.put(flag, e.getValue());
                } catch (IllegalArgumentException ex) {
                    log.warn("[GrayRelease] 未知 flag 名 '{}' 跳过", e.getKey());
                }
            }
            flagCache = result;
            flagCacheExpireAt = System.currentTimeMillis() + CACHE_TTL_SECONDS * 1000;
            return result;
        } catch (Exception e) {
            log.warn("[GrayRelease] 读取 flags 失败（fail-closed）: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 直接读取当前灰度白名单（绕过缓存）
     */
    public Set<Long> loadGrayUsersFromRedis() {
        try {
            Set<String> members = redisTemplate.opsForSet().members(GRAY_USERS_KEY);
            if (members == null || members.isEmpty()) {
                grayUserCache = Collections.emptySet();
                grayUserCacheExpireAt = System.currentTimeMillis() + CACHE_TTL_SECONDS * 1000;
                return Collections.emptySet();
            }
            Set<Long> result = new HashSet<>();
            for (String m : members) {
                try {
                    result.add(Long.parseLong(m));
                } catch (NumberFormatException ex) {
                    log.warn("[GrayRelease] 无效 userId 跳过: {}", m);
                }
            }
            grayUserCache = result;
            grayUserCacheExpireAt = System.currentTimeMillis() + CACHE_TTL_SECONDS * 1000;
            return result;
        } catch (Exception e) {
            log.warn("[GrayRelease] 读取灰度白名单失败（fail-closed）: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private Map<FeatureFlag, Boolean> getFlagCacheSnapshot() {
        if (System.currentTimeMillis() > flagCacheExpireAt) {
            return loadFlagsFromRedis();
        }
        return flagCache;
    }

    private Set<Long> getGrayUserCacheSnapshot() {
        if (System.currentTimeMillis() > grayUserCacheExpireAt) {
            return loadGrayUsersFromRedis();
        }
        return grayUserCache;
    }

    /**
     * 业务断言：功能开关启用。禁用时抛 BUSINESS_DISABLED 业务异常
     * （HTTP 503 — 客户端可识别为"功能维护中"而非"系统错误"）
     */
    public void assertFeatureEnabled(FeatureFlag flag) {
        if (!isFeatureEnabled(flag)) {
            throw new BusinessException(ErrorCode.FEATURE_DISABLED,
                    "功能 " + flag.name() + " 当前未启用");
        }
    }
}