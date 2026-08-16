package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.UserUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.entity.Enrollment;
import com.microcourse.enums.CourseStatus;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.service.EnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import com.jayway.jsonpath.JsonPath;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P2-3: @Valid null 放行场景专项测试。
 *
 * 【根因】部分更新 DTO 中 null 表示"不更新该字段"，Service 层做 if (dto.getXxx() != null) 判断后选择性更新。
 * 若 DTO 字段上有 @NotNull 约束，则 null 放行时 @Valid 会拦截并返回 400，导致正常部分更新被误杀。
 *
 * 【测试策略】
 * 1. 发送所有字段为 null 的 UpdateRequest，验证 @Valid 不拦截（返回 200 而非 400）
 * 2. 验证 Service 层正确忽略 null 字段（原有值保持不变）
 * 3. 验证非 null 字段正确更新
 */
@DisplayName("P2-3 @Valid null 放行专项测试")
class PartialUpdateNullSafeTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private com.microcourse.service.EnrollmentService enrollmentService;

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    private String uniq() {
        return Long.toString(System.nanoTime()) + "-" + SEQ.incrementAndGet();
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    @DisplayName("EnrollmentUpdateRequest 全 null 字段应被 @Valid 放行（HTTP 200 而非 400）")
    void enrollmentUpdateAllNull_shouldPassValidation() throws Exception {
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long courseId = insertCourse(catId, teacherId, CourseStatus.PUBLISHED.getCode());
        Long studentId = insertUser("STUDENT");
        Long enrollId = insertEnrollment(studentId, courseId);

        EnrollmentUpdateRequest req = new EnrollmentUpdateRequest();
        assertNull(req.getProgress());
        assertNull(req.getCompleted());
        assertNull(req.getFinalScore());
        assertNull(req.getFinalGrade());
        assertNull(req.getEnrollmentStatus());

        mockMvc.perform(put("/api/enrollments/{id}", enrollId)
                        .contentType("application/json")
                        .content(toJson(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("EnrollmentUpdateRequest 全 null 字段应保留原值（Service 层正确忽略 null）")
    void enrollmentUpdateAllNull_shouldPreserveOriginalValues() {
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long courseId = insertCourse(catId, teacherId, CourseStatus.PUBLISHED.getCode());
        Long studentId = insertUser("STUDENT");
        Long enrollId = insertEnrollmentWithProgress(studentId, courseId, 50.0);

        EnrollmentUpdateRequest req = new EnrollmentUpdateRequest();
        enrollmentService.updateEnrollment(enrollId, req);

        Enrollment updated = enrollmentRepository.selectById(enrollId);
        assertEquals(50.0, updated.getProgress(),
                "进度应保持 50.0（null 字段被 Service 层忽略）");
    }

    @Test
    @DisplayName("EnrollmentUpdateRequest 非 null 字段应正确更新，null 字段应保留")
    void enrollmentUpdatePartial_shouldUpdateOnlyNonNullFields() {
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long courseId = insertCourse(catId, teacherId, CourseStatus.PUBLISHED.getCode());
        Long studentId = insertUser("STUDENT");
        Long enrollId = insertEnrollmentWithProgress(studentId, courseId, 50.0);

        EnrollmentUpdateRequest req = new EnrollmentUpdateRequest();
        req.setProgress(75.0);

        EnrollmentVO vo = enrollmentService.updateEnrollment(enrollId, req);
        assertEquals(75.0, vo.getProgress(), "非 null 字段应更新");
    }

    @Test
    @DisplayName("UserUpdateRequest 全 null 字段应被 @Valid 放行")
    void userUpdateAllNull_shouldPassValidation() throws Exception {
        Long userId = insertUser("STUDENT");

        UserUpdateRequest req = new UserUpdateRequest();

        mockMvc.perform(put("/api/users/{id}", userId)
                        .contentType("application/json")
                        .content(toJson(req)))
                .andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private Long insertCategory() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO course_categories(name, level, sort_order, created_at, updated_at) " +
                "VALUES (?, 1, 0, now(), now()) RETURNING id",
                Long.class, "tcat-" + uniq());
    }

    private Long insertUser(String role) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 1, false, now(), now()) RETURNING id",
                Long.class, "u-" + uniq(),
                "$2b$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV",
                "测试" + role, role);
    }

    private Long insertCourse(Long categoryId, Long teacherId, int status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, true, 0, now(), now()) RETURNING id",
                Long.class, "tcourse-" + uniq(), categoryId, teacherId, status);
    }

    private Long insertEnrollment(Long userId, Long courseId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO enrollments(course_id, user_id, progress, completed, enrollment_status, enrolled_at, updated_at) " +
                "VALUES (?, ?, 0, false, 'APPROVED', now(), now()) RETURNING id",
                Long.class, courseId, userId);
    }

    private Long insertEnrollmentWithProgress(Long userId, Long courseId, Double progress) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO enrollments(course_id, user_id, progress, completed, enrollment_status, enrolled_at, updated_at) " +
                "VALUES (?, ?, ?, false, 'APPROVED', now(), now()) RETURNING id",
                Long.class, courseId, userId, progress);
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
