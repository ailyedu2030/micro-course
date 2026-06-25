package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.enums.UserRole;
import com.microcourse.repository.UserRepository;
import com.microcourse.security.UserStatusCheckFilter;
import com.microcourse.service.UserService;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round 8-2 商业致命 P0 修复测试：被禁用/删除用户的 JWT 在生命周期内不再有效。
 *
 * <p>覆盖 {@link UserStatusCheckFilter} 五大行为：</p>
 * <ol>
 *   <li>DISABLED 用户携旧 token 访问受保护端点 → 401（禁用立即生效）；</li>
 *   <li>DELETED 用户携旧 token 访问 → 401；</li>
 *   <li>ACTIVE 用户访问 → 200（合法用户零退化）；</li>
 *   <li>Redis + DB 双重故障 → fail-open 放行（不阻塞合法用户主流程）；</li>
 *   <li>状态变更后 user:status 缓存被立即清除（禁用/解禁即时生效）。</li>
 * </ol>
 *
 * <p>受保护端点选用 {@code GET /api/users/{id}/public-profile}（@PreAuthorize isAuthenticated），
 * 任意已登录用户可访问，便于聚焦验证「状态校验」而非业务授权。</p>
 */
@DisplayName("Round 8-2 禁用用户 Token 失效 Filter")
class UserStatusCheckFilterTest extends BaseIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private UserService userService;

    /** 本类内旁路插入的测试用户 ID，@AfterEach 统一物理清理，避免污染共享种子库。 */
    private final List<Long> createdUserIds = new ArrayList<>();

    // ───────────────────── helpers ─────────────────────

    private String uniq() {
        return Long.toString(System.nanoTime()) + "-" + SEQ.incrementAndGet();
    }

    /** 旁路插入 STUDENT 用户（status / deleted_at 由参数指定），返回自增 ID。 */
    private Long insertStudent(int status, Timestamp deletedAt) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, deleted_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'STUDENT', ?, false, ?, now(), now()) RETURNING id",
                Long.class,
                "r82test-" + uniq(),
                "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv",
                "Round8-2测试用户",
                status,
                deletedAt);
        createdUserIds.add(id);
        return id;
    }

    /** 为指定 userId 直签一个有效 STUDENT JWT（绕过登录密码，token 仅 userId 参与状态校验）。 */
    private String bearerFor(Long userId) {
        return "Bearer " + jwtUtil.generateToken(userId, "r82user", UserRole.STUDENT, null);
    }

    private UserStatusRequest statusReq(int code) {
        UserStatusRequest r = new UserStatusRequest();
        r.setStatus(code);
        return r;
    }

    private void clearStatusCache(Long id) {
        try {
            redisUtil.delete(UserStatusCheckFilter.STATUS_CACHE_PREFIX + id);
        } catch (Exception ignored) {
        }
    }

    @AfterEach
    void cleanupCreatedUsers() {
        for (Long id : createdUserIds) {
            try {
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
            } catch (Exception ignored) {
            }
            clearStatusCache(id);
        }
        createdUserIds.clear();
    }

    // ───────────────────── tests ─────────────────────

    @Test
    @DisplayName("被禁用用户携旧 token 访问受保护端点应返回 401")
    void disabledUserCannotAccessProtectedEndpoint() throws Exception {
        Long uid = insertStudent(1, null); // ACTIVE
        clearStatusCache(uid);
        // 管理员禁用（ACTIVE → DISABLED 合法转换，updateStatus 内部会清缓存）
        userService.updateStatus(uid, statusReq(2));

        mockMvc.perform(get("/api/users/" + uid + "/public-profile")
                        .header("Authorization", bearerFor(uid)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("被删除用户携旧 token 访问受保护端点应返回 401")
    void deletedUserCannotAccessProtectedEndpoint() throws Exception {
        Long uid = insertStudent(1, null); // ACTIVE
        clearStatusCache(uid);
        // ACTIVE → DELETED 合法转换（软删除，置 status=3 + deleted_at）
        userService.updateStatus(uid, statusReq(3));

        mockMvc.perform(get("/api/users/" + uid + "/public-profile")
                        .header("Authorization", bearerFor(uid)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正常用户访问受保护端点应放行（合法用户零退化）")
    void activeUserCanAccessProtectedEndpoint() throws Exception {
        Long uid = insertStudent(1, null); // ACTIVE
        clearStatusCache(uid);

        mockMvc.perform(get("/api/users/" + uid + "/public-profile")
                        .header("Authorization", bearerFor(uid)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Redis 与 DB 双重故障时 fail-open 放行，不阻塞合法用户主流程")
    void redisFailureDoesNotBlockMainFlow() throws Exception {
        // 不使用 Mockito（当前 JVM=Java 26，ByteBuddy inline mock 无法 mock 具体类 RedisUtil）：
        // 改用 JDK 动态代理伪造「DB 故障」的 UserRepository + 继承式伪造「Redis 故障」的 RedisUtil，
        // 与 JVM 版本无关、零额外依赖。
        UserRepository failingRepo = (UserRepository) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{UserRepository.class},
                (proxy, method, args) -> {
                    throw new RuntimeException("db down");
                });
        RedisUtil failingRedis = new FailingRedisUtil();

        UserStatusCheckFilter filter = new UserStatusCheckFilter(failingRepo, failingRedis);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                999L, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            MockHttpServletRequest req = new MockHttpServletRequest();
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            // fail-open：请求被放行至后续过滤链，且未被 401 拦截
            assertNotNull(chain.getRequest(), "fail-open 时请求应被放行到后续链");
            assertNotEquals(401, res.getStatus(), "fail-open 时不应返回 401");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** 伪造「Redis 故障」的 RedisUtil：读写均抛异常，用于验证 fail-safe 降级 / fail-open 放行。 */
    private static class FailingRedisUtil extends RedisUtil {
        FailingRedisUtil() {
            super(null); // 不接触真实 RedisTemplate（下述 override 覆盖所有被调用方法）
        }

        @Override
        public Object get(String key) {
            throw new RuntimeException("redis down");
        }

        @Override
        public void set(String key, Object value, long timeout, TimeUnit unit) {
            throw new RuntimeException("redis down");
        }
    }

    @Test
    @DisplayName("用户状态变更后 user:status 缓存应被立即清除")
    void cacheIsClearedWhenUserStatusChanges() {
        Long uid = insertStudent(1, null); // ACTIVE
        String cacheKey = UserStatusCheckFilter.STATUS_CACHE_PREFIX + uid;

        // 预置缓存，模拟此前 Filter 已写入旧状态
        redisUtil.set(cacheKey, "ACTIVE", 30, TimeUnit.SECONDS);
        assertNotNull(redisUtil.get(cacheKey), "前置条件：状态缓存应已写入");

        // 禁用用户（ACTIVE → DISABLED），updateStatus 应主动清缓存
        userService.updateStatus(uid, statusReq(2));

        assertNull(redisUtil.get(cacheKey), "状态变更后缓存应被立即清除（禁用即时生效）");
    }
}
