package com.microcourse.repository;

import com.microcourse.entity.Enrollment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EnrollmentRepository 自定义 SQL 隔离测试。
 * <p>依赖 p0-seed.sql 提供的基础数据：user(id=6 teacher, id=7 student), courses(1-4, teacher_id=6)。</p>
 *
 * <h3>【现象】测试覆盖确保每次变更后 EnrollmentRepository 自定义 SQL 均被验证</h3>
 * <h3>【根因】选课核心 SQL 含 AVG 聚合、原子 INSERT…SELECT、物理 DELETE 等复杂逻辑，需隔离测试守护</h3>
 * <h3>【验证】mvn test -Dtest='EnrollmentRepositoryTest' PASS</h3>
 * <h3>【防止再发】追加此测试类，后续变更 EnrollmentRepository 时同步更新</h3>
 *
 * <p>注意：使用 (user_id=7, course_id=2) 和 (user_id=6, course_id=2) 组合避免与存量 stale 数据冲突。</p>
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@Sql(scripts = {"/sql/repo-test-cleanup.sql", "/sql/p0-seed.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EnrollmentRepositoryTest {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    // ==================== avgScoreByTeacherId ====================

    @Test
    @DisplayName("avgScoreByTeacherId: 有评分记录时返回正确均值")
    void avgScoreByTeacherId_withScores_returnsAverage() {
        // 课程 2 (teacher_id=6) 下两个选课记录各 80 和 90 → avg=85
        insertEnrollment(7L, 2L, "APPROVED", new BigDecimal("80.00"), 100.0, true);
        insertEnrollment(6L, 2L, "COMPLETED", new BigDecimal("90.00"), 100.0, true);

        Double avg = enrollmentRepository.avgScoreByTeacherId(6L);
        assertNotNull(avg);
        assertEquals(85.0, avg, 0.001);
    }

    @Test
    @DisplayName("avgScoreByTeacherId: 无评分记录时返回 null")
    void avgScoreByTeacherId_noScores_returnsNull() {
        insertEnrollment(7L, 2L, "APPROVED", null, 50.0, true);

        Double avg = enrollmentRepository.avgScoreByTeacherId(6L);
        assertNull(avg);
    }

    @Test
    @DisplayName("avgScoreByTeacherId: 无选课记录时返回 null")
    void avgScoreByTeacherId_noEnrollments_returnsNull() {
        Double avg = enrollmentRepository.avgScoreByTeacherId(999L);
        assertNull(avg);
    }

    // ==================== avgScoreByCourseId ====================

    @Test
    @DisplayName("avgScoreByCourseId: 有评分时返回正确均值")
    void avgScoreByCourseId_withScores_returnsAverage() {
        insertEnrollment(7L, 2L, "APPROVED", new BigDecimal("75.00"), 100.0, true);

        Double avg = enrollmentRepository.avgScoreByCourseId(2L);
        assertNotNull(avg);
        assertEquals(75.0, avg, 0.001);
    }

    @Test
    @DisplayName("avgScoreByCourseId: 无评分记录时返回 null")
    void avgScoreByCourseId_noScores_returnsNull() {
        Double avg = enrollmentRepository.avgScoreByCourseId(999L);
        assertNull(avg);
    }

    // ==================== countCompletedByCourseIds ====================

    @Test
    @DisplayName("countCompletedByCourseIds: 批量统计完成选课数")
    void countCompletedByCourseIds_returnsCounts() {
        insertEnrollment(7L, 2L, "COMPLETED", new BigDecimal("88.00"), 100.0, true);

        List<Map<String, Object>> result = enrollmentRepository.countCompletedByCourseIds(
                List.of(2L, 3L), "COMPLETED");

        assertNotNull(result);
        boolean found = false;
        for (Map<String, Object> row : result) {
            if (((Number) row.get("course_id")).longValue() == 2L) {
                assertEquals(1L, ((Number) row.get("cnt")).longValue());
                found = true;
            }
        }
        assertTrue(found, "课程 2 应出现在统计结果中");
    }

    // ==================== countInProgressOrCompletedByCourseIds ====================

    @Test
    @DisplayName("countInProgressOrCompletedByCourseIds: 统计进行中和已完成的选课")
    void countInProgressOrCompletedByCourseIds_returnsCounts() {
        insertEnrollment(7L, 2L, "APPROVED", null, 50.0, true);
        insertEnrollment(6L, 2L, "COMPLETED", new BigDecimal("95.00"), 100.0, true);

        List<Map<String, Object>> result = enrollmentRepository.countInProgressOrCompletedByCourseIds(
                List.of(2L), "ENROLLED", "APPROVED", "COMPLETED");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(2L, ((Number) result.get(0).get("course_id")).longValue());
    }

    // ==================== physicalDeleteById ====================

    @Test
    @DisplayName("physicalDeleteById: 物理删除记录")
    void physicalDeleteById_deletesRecord() {
        Enrollment e = insertEnrollment(7L, 2L, "APPROVED", null, 0.0, true);
        Long id = e.getId();

        int affected = enrollmentRepository.physicalDeleteById(id);
        assertEquals(1, affected, "物理删除应影响 1 行");
    }

    @Test
    @DisplayName("physicalDeleteById: 删除不存在的 ID 返回 0")
    void physicalDeleteById_notFound_returnsZero() {
        int affected = enrollmentRepository.physicalDeleteById(99999L);
        assertEquals(0, affected);
    }

    // ==================== atomicInsertIfCapacity ====================

    @Test
    @DisplayName("atomicInsertIfCapacity: 容量足够时插入成功")
    void atomicInsertIfCapacity_success() {
        int affected = enrollmentRepository.atomicInsertIfCapacity(7L, 2L, "PENDING", "WEB");
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("atomicInsertIfCapacity: 重复选课被 NOT EXISTS 阻止")
    void atomicInsertIfCapacity_duplicate_returnsZero() {
        enrollmentRepository.atomicInsertIfCapacity(7L, 2L, "PENDING", "WEB");
        int second = enrollmentRepository.atomicInsertIfCapacity(7L, 2L, "PENDING", "WEB");
        assertEquals(0, second, "重复选课应被阻止");
    }

    // ==================== atomicInsertIfEnrollable ====================

    @Test
    @DisplayName("atomicInsertIfEnrollable: 可插入时成功")
    void atomicInsertIfEnrollable_success() {
        int affected = enrollmentRepository.atomicInsertIfEnrollable(7L, 2L, "WAITLIST", "WEB");
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("atomicInsertIfEnrollable: 重复添加被阻止")
    void atomicInsertIfEnrollable_duplicate_returnsZero() {
        enrollmentRepository.atomicInsertIfEnrollable(7L, 2L, "WAITLIST", "WEB");
        int second = enrollmentRepository.atomicInsertIfEnrollable(7L, 2L, "WAITLIST", "WEB");
        assertEquals(0, second);
    }

    // ==================== countWaitlistByCourseId ====================

    @Test
    @DisplayName("countWaitlistByCourseId: 统计 WAITLIST 人数")
    void countWaitlistByCourseId_returnsCount() {
        enrollmentRepository.atomicInsertIfEnrollable(7L, 2L, "WAITLIST", "WEB");
        int count = enrollmentRepository.countWaitlistByCourseId(2L, "WAITLIST");
        assertEquals(1, count);
    }

    @Test
    @DisplayName("countWaitlistByCourseId: 无候补返回 0")
    void countWaitlistByCourseId_noWaitlist_returnsZero() {
        int count = enrollmentRepository.countWaitlistByCourseId(999L, "WAITLIST");
        assertEquals(0, count);
    }

    // ==================== findActiveUserIdsByCourseId ====================

    @Test
    @DisplayName("findActiveUserIdsByCourseId: 返回活跃学生 ID")
    void findActiveUserIdsByCourseId_returnsActiveUsers() {
        insertEnrollment(7L, 2L, "APPROVED", null, 25.0, true);

        List<Long> ids = enrollmentRepository.findActiveUserIdsByCourseId(2L, "ENROLLED", "APPROVED", "COMPLETED");
        assertFalse(ids.isEmpty());
        assertTrue(ids.contains(7L));
    }

    @Test
    @DisplayName("findActiveUserIdsByCourseId: 无活跃选课返回空")
    void findActiveUserIdsByCourseId_noActive_returnsEmpty() {
        List<Long> ids = enrollmentRepository.findActiveUserIdsByCourseId(999L, "ENROLLED", "APPROVED", "COMPLETED");
        assertTrue(ids.isEmpty());
    }

    // ==================== countByTeacherAndStudent ====================

    @Test
    @DisplayName("countByTeacherAndStudent: 有权查看时返回 >0")
    void countByTeacherAndStudent_hasAccess() {
        insertEnrollment(7L, 2L, "APPROVED", null, 25.0, true);

        long count = enrollmentRepository.countByTeacherAndStudent(6L, 7L, "ENROLLED", "APPROVED", "COMPLETED");
        assertTrue(count > 0);
    }

    @Test
    @DisplayName("countByTeacherAndStudent: 无权查看时返回 0")
    void countByTeacherAndStudent_noAccess() {
        long count = enrollmentRepository.countByTeacherAndStudent(6L, 999L, "ENROLLED", "APPROVED", "COMPLETED");
        assertEquals(0L, count);
    }

    // ==================== helper ====================

    private Enrollment insertEnrollment(Long userId, Long courseId, String status,
                                         BigDecimal finalScore, Double progress, boolean setFinalScore) {
        Enrollment e = new Enrollment();
        e.setUserId(userId);
        e.setCourseId(courseId);
        e.setEnrollmentStatus(status);
        if (setFinalScore) {
            e.setFinalScore(finalScore);
        }
        e.setProgress(progress);
        e.setCompleted("COMPLETED".equals(status));
        e.setSourceChannel("WEB");
        e.setEnrolledAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        e.setVersion(0);
        enrollmentRepository.insert(e);
        return e;
    }
}
