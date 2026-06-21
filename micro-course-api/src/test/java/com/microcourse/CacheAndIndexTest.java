package com.microcourse;

import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.service.CourseService;
import com.microcourse.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Round 9-2 · 缓存层验证（课程详情 / 课程统计 Redis 缓存 + 一致性 + 降级）。
 *
 * <p>覆盖 5 大 UX 硬约束中的：
 * <ul>
 *   <li>#1 缓存一致性：数据更新 → 缓存失效（{@link #courseDetailCacheShouldBeEvictedOnUpdate}）。</li>
 *   <li>#2 缓存击穿/穿透防护：缓存数据不可用时降级查 DB（{@link #redisFailureShouldFallbackToDatabase}）。</li>
 * </ul>
 * 通过真实 Postgres（JdbcTemplate 直插，满足 courses NOT NULL + FK 约束）+ 真实 Redis 验证。
 */
@DisplayName("Round9-2 缓存层")
class CacheAndIndexTest extends BaseIntegrationTest {

    private static final String COURSE_CACHE_PREFIX = "course:detail:";
    private static final String COURSE_STATS_CACHE_PREFIX = "course:stats:";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CourseService courseService;
    @Autowired
    private RedisUtil redisUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCourseIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @AfterEach
    void cleanupCache() {
        // 清 Redis 缓存键，避免污染其它用例
        for (Long c : createdCourseIds) {
            try { redisUtil.delete(COURSE_CACHE_PREFIX + c); } catch (Exception ignored) {}
            try { redisUtil.delete(COURSE_STATS_CACHE_PREFIX + c); } catch (Exception ignored) {}
        }
        // FK 安全顺序：courses → users → course_categories
        for (Long c : createdCourseIds) {
            try { jdbc.update("DELETE FROM course_chapters WHERE course_id = ?", c); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM courses WHERE id = ?", c); } catch (Exception ignored) {}
        }
        for (Long u : createdUserIds) {
            try { jdbc.update("DELETE FROM users WHERE id = ?", u); } catch (Exception ignored) {}
        }
        for (Long cat : createdCategoryIds) {
            try { jdbc.update("DELETE FROM course_categories WHERE id = ?", cat); } catch (Exception ignored) {}
        }
        createdUserIds.clear();
        createdCourseIds.clear();
        createdCategoryIds.clear();
        SecurityContextHolder.clearContext();
    }

    // --------- fixtures（复用 EnrollmentFlowIntegrationTest 的直插范式）---------

    private Long insertCategory() {
        Long id = jdbc.queryForObject(
                "INSERT INTO course_categories(name, level, sort_order, created_at, updated_at) " +
                        "VALUES (?, 1, 0, now(), now()) RETURNING id",
                Long.class, "cache-cat-" + System.nanoTime());
        createdCategoryIds.add(id);
        return id;
    }

    private Long insertTeacher() {
        String username = "cache-teacher-" + System.nanoTime();
        Long id = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'TEACHER', 1, false, now(), now()) RETURNING id",
                Long.class, username,
                "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv", "缓存测试教师");
        createdUserIds.add(id);
        return id;
    }

    private Long insertCourse(Long categoryId, Long teacherId, int status) {
        Long id = jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, course_type, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, true, 0, 'VIDEO', 0, now(), now()) RETURNING id",
                Long.class, "cache-course-" + System.nanoTime(), categoryId, teacherId, status);
        createdCourseIds.add(id);
        return id;
    }

    private Long newDraftCourse() {
        Long cat = insertCategory();
        Long teacher = insertTeacher();
        return insertCourse(cat, teacher, 0); // 0 = DRAFT
    }

    private void loginAsAdminContext() {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --------- 1 · 课程详情写入缓存 ---------
    @Test
    @DisplayName("1·getById 后课程详情写入 Redis 缓存")
    void courseDetailShouldBeCached() {
        Long courseId = newDraftCourse();
        String key = COURSE_CACHE_PREFIX + courseId;
        // 前置：缓存为空
        assertNull(redisUtil.get(key), "用例开始时缓存应为空");

        CourseVO vo = courseService.getById(courseId); // miss → 查 DB → 写缓存
        assertNotNull(vo, "应从 DB 返回课程");
        assertEquals(courseId, vo.getId());

        Object cached = redisUtil.get(key);
        assertNotNull(cached, "getById 后缓存应存在");
        assertInstanceOf(CourseVO.class, cached, "缓存值应可还原为 CourseVO");
        assertEquals(courseId, ((CourseVO) cached).getId());
    }

    // --------- 2 · 更新清除缓存（一致性 硬约束#1）---------
    @Test
    @DisplayName("2·update 后课程详情缓存被清除（一致性）")
    void courseDetailCacheShouldBeEvictedOnUpdate() {
        Long courseId = newDraftCourse();
        String key = COURSE_CACHE_PREFIX + courseId;

        courseService.getById(courseId); // 触发缓存
        assertNotNull(redisUtil.get(key), "getById 后缓存应已写入");

        loginAsAdminContext(); // update 需 ADMIN/Owner 权限
        CourseUpdateRequest req = new CourseUpdateRequest();
        req.setTitle("Round9-2 更新后的标题");
        courseService.update(courseId, req); // 更新 → afterCommit 清除缓存

        assertNull(redisUtil.get(key), "update 后缓存应被清除");
    }

    // --------- 3 · 缓存不可用时降级查 DB（硬约束#2）---------
    @Test
    @DisplayName("3·缓存值损坏/不可用时降级回 DB（不抛异常）")
    void redisFailureShouldFallbackToDatabase() {
        Long courseId = newDraftCourse();
        String key = COURSE_CACHE_PREFIX + courseId;

        // 模拟 Redis 返回不可用数据（损坏的缓存值，非 CourseVO）
        redisUtil.set(key, "corrupted-cache-value", 60, TimeUnit.SECONDS);

        // instanceof 守卫应拒绝损坏值 → 降级查 DB → 返回有效课程，且不抛异常
        CourseVO vo = courseService.getById(courseId);
        assertNotNull(vo, "缓存不可用时应降级从 DB 返回");
        assertEquals(courseId, vo.getId());

        // 降级后应以 DB 结果重写缓存为合法 CourseVO
        Object cached = redisUtil.get(key);
        assertInstanceOf(CourseVO.class, cached, "降级后缓存应被覆盖为合法 CourseVO");
    }

    // --------- 4 · 课程统计写入缓存 ---------
    @Test
    @DisplayName("4·computeStats 后课程统计写入 Redis 缓存")
    void courseStatsShouldBeCached() {
        Long courseId = newDraftCourse();
        String key = COURSE_STATS_CACHE_PREFIX + courseId;
        assertNull(redisUtil.get(key), "用例开始时统计缓存应为空");

        courseService.computeStats(courseId); // miss → 计算 → 写缓存
        assertNotNull(redisUtil.get(key), "computeStats 后统计缓存应存在");
    }
}
