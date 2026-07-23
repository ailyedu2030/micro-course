package com.microcourse.security;

import com.microcourse.dto.R;
import com.microcourse.entity.User;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.util.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 用户状态检查 Filter（Round 8-2 商业致命 P0 修复）。
 *
 * <p>解决「禁用/删除用户的 JWT 在生命周期内仍然有效」的安全致命缺陷：
 * 管理员将用户 status 改为 DISABLED/DELETED 后，旧 JWT 在默认 2 小时有效期内
 * 仍可畅通访问所有 API，使禁用形同虚设。本 Filter 在认证链上补齐「状态校验」环节。</p>
 *
 * <p>工作原理（在 {@code JwtAuthenticationFilter} 之后执行，此时 SecurityContext 已填充）：</p>
 * <ol>
 *   <li>仅对已认证请求（principal 为 Long userId）生效；匿名/未认证请求直接放行，交后续授权层处理。</li>
 *   <li>读取 userId 对应的 {@code user.status}（Redis 缓存优先，TTL 30 秒，兼顾性能与实时性）。</li>
 *   <li>若状态为 DISABLED/DELETED：清空 SecurityContext，返回 401（账号已被禁用或删除）。</li>
 *   <li>Redis 读/写失败 → 降级查 DB（fail-safe）；整体校验异常 → 放行（fail-open），不阻塞合法用户。</li>
 * </ol>
 *
 * <p>UX 零退化要点：缓存命中时不查 DB，合法用户无感知；状态变更时由
 * {@code UserServiceImpl.updateStatus} 主动清缓存，确保禁用/解禁立即生效。</p>
 *
 * <p>禁用 Lombok（见 pom.xml 注释 / precheck.sh check_lombok_import）：手写 Logger 与构造器注入。</p>
 */
@Component
public class UserStatusCheckFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserStatusCheckFilter.class);

    /** 用户状态缓存 key 前缀（供 UserServiceImpl 状态变更时清缓存复用，保证前缀一致）。 */
    public static final String STATUS_CACHE_PREFIX = "mc:user:status:";

    /** 缓存 TTL（秒）：30 秒在「禁用生效延迟上限」与「DB 压力」之间取平衡。 */
    private static final long STATUS_CACHE_TTL = 30L;

    private final UserRepository userRepository;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    public UserStatusCheckFilter(UserRepository userRepository, RedisUtil redisUtil, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 仅对「已认证且 principal 为 Long userId」的请求做状态校验。
        // 匿名请求（principal 为 "anonymousUser" 字符串）或无认证信息时放行，交后续授权层决定。
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Long userId = (Long) authentication.getPrincipal();

            String status = getUserStatus(userId);
            if ("DISABLED".equals(status)) {
                log.warn("禁用用户尝试访问系统: userId={}", userId);
                SecurityContextHolder.clearContext();
                writeErrorResponse(response, ErrorCode.ACCOUNT_DISABLED);
                return;
            }
            if ("DELETED".equals(status)) {
                log.warn("删除用户尝试访问系统: userId={}", userId);
                SecurityContextHolder.clearContext();
                writeErrorResponse(response, ErrorCode.ACCOUNT_DELETED);
                return;
            }
        } catch (Exception e) {
            // fail-open 路径已明确放行，记录为 warn 避免把降级误报成主流程故障。
            log.warn("用户状态检查异常（fail-open 放行）", e);
        }

        chain.doFilter(request, response);
    }

    /**
     * 获取用户状态字符串（ACTIVE/DISABLED/DELETED/INACTIVE/UNKNOWN）。
     *
     * <p>缓存优先；缓存未命中或读取失败时降级查 DB 并回填缓存。selectById 受全局逻辑删除过滤，
     * DELETED（deleted_at 非空）或不存在的用户均返回 null，统一归为 "DELETED" 拒绝访问。</p>
     */
    private String getUserStatus(Long userId) {
        String cacheKey = STATUS_CACHE_PREFIX + userId;

        // 1) 读缓存（失败不阻塞，降级查 DB）
        try {
            Object cached = redisUtil.get(cacheKey);
            if (cached != null) {
                return cached.toString();
            }
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败（降级查 DB）: key={}", cacheKey, e);
        }

        // 2) 查 DB（selectById 自动追加 deleted_at IS NULL：DELETED/不存在用户均返回 null）
        User user = userRepository.selectById(userId);
        String status = (user == null) ? "DELETED" : mapStatusToString(user.getStatus());

        // 3) 回填缓存（失败不影响主流程）
        try {
            redisUtil.set(cacheKey, status, STATUS_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 缓存写入失败: key={}", cacheKey, e);
        }

        return status;
    }

    /** 将 users.status 整型列映射为状态字符串（与 UserStatus 枚举值一一对应）。 */
    private String mapStatusToString(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        switch (status) {
            case 0:
                return "INACTIVE";
            case 1:
                return "ACTIVE";
            case 2:
                return "DISABLED";
            case 3:
                return "DELETED";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 写入符合 R&lt;T&gt; 契约格式的错误响应（含 timestamp）。
     * 使用 ObjectMapper 序列化 R.fail(ErrorCode)，避免 String.format 拼接导致的特殊字符问题。
     */
    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(errorCode)));
    }
}
