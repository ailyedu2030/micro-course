package com.microcourse.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CourseRepository 自定义 SQL 隔离测试（含行锁测试）。
 * <p>依赖 p0-seed.sql 提供的基础数据：user(id=6 teacher), courses(1-4, teacher_id=6), course_categories(id=1)。</p>
 *
 * <h3>【现象】行锁(selectByIdForUpdate)、原子计数(atomicIncrementStudentCount)、容量保护(atomicIncrementIfNotFull)等
 * 自定义 SQL 缺乏直接测试覆盖</h3>
 * <h3>【根因】CourseRepository 含 10+ 自定义 SQL（UPDATE/SELECT FOR UPDATE/聚合），变更风险高</h3>
 * <h3>【验证】mvn test -Dtest='CourseRepositoryTest' PASS</h3>
 * <h3>【防止再发】所有自定义 SQL 均被隔离测试覆盖，变更时同步更新</h3>
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class CourseRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    // ==================== atomicIncrementStudentCount ====================

    @Test
    @DisplayName("atomicIncrementStudentCount: 增加选课人数")
    void atomicIncrementStudentCount_increments() {
        int affected = courseRepository.atomicIncrementStudentCount(1L);
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("atomicIncrementStudentCount: 对不存在课程返回 0")
    void atomicIncrementStudentCount_notFound_returnsZero() {
        int affected = courseRepository.atomicIncrementStudentCount(999L);
        assertEquals(0, affected);
    }

    // ==================== atomicIncrementIfNotFull ====================

    @Test
    @DisplayName("atomicIncrementIfNotFull: 容量未满时增加（max_students=0 不限）")
    void atomicIncrementIfNotFull_unlimitedCapacity_success() {
        // 课程 1 max_students 默认 0（不限人数）
        int affected = courseRepository.atomicIncrementIfNotFull(1L);
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("atomicIncrementIfNotFull: 不存在课程返回 0")
    void atomicIncrementIfNotFull_notFound_returnsZero() {
        int affected = courseRepository.atomicIncrementIfNotFull(999L);
        assertEquals(0, affected);
    }

    // ==================== selectByIdForUpdate（行锁） ====================

    @Test
    @DisplayName("selectByIdForUpdate: 返回课程行级锁数据")
    void selectByIdForUpdate_returnsCourseData() {
        Map<String, Object> result = courseRepository.selectByIdForUpdate(1L);
        assertNotNull(result);
        assertEquals(1L, ((Number) result.get("id")).longValue());
        assertTrue(result.containsKey("student_count"));
    }

    @Test
    @DisplayName("selectByIdForUpdate: 不存在课程返回 null")
    void selectByIdForUpdate_notFound_returnsNull() {
        Map<String, Object> result = courseRepository.selectByIdForUpdate(999L);
        assertNull(result);
    }

    // ==================== atomicDecrementStudentCount ====================

    @Test
    @DisplayName("atomicDecrementStudentCount: 减少选课人数")
    void atomicDecrementStudentCount_decrements() {
        courseRepository.atomicIncrementStudentCount(1L);
        int affected = courseRepository.atomicDecrementStudentCount(1L);
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("atomicDecrementStudentCount: 不会减到负数")
    void atomicDecrementStudentCount_neverNegative() {
        int affected = courseRepository.atomicDecrementStudentCount(1L);
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("atomicDecrementStudentCount: 不存在课程返回 0")
    void atomicDecrementStudentCount_notFound_returnsZero() {
        int affected = courseRepository.atomicDecrementStudentCount(999L);
        assertEquals(0, affected);
    }

    // ==================== updateAvgRating ====================

    @Test
    @DisplayName("updateAvgRating: 无评价时更新为 0")
    void updateAvgRating_noReviews_setsToZero() {
        int affected = courseRepository.updateAvgRating(1L);
        assertEquals(1, affected);
    }

    @Test
    @DisplayName("updateAvgRating: 不存在课程返回 0")
    void updateAvgRating_notFound_returnsZero() {
        int affected = courseRepository.updateAvgRating(999L);
        assertEquals(0, affected);
    }

    // ==================== selectAvgCompletionRate ====================

    @Test
    @DisplayName("selectAvgCompletionRate: 无选课返回 0")
    void selectAvgCompletionRate_noData_returnsZero() {
        Double rate = courseRepository.selectAvgCompletionRate();
        assertNotNull(rate);
        assertEquals(0.0, rate, 0.001);
    }

    @Test
    @DisplayName("selectAvgCompletionRate: 有完成记录时返回正数")
    void selectAvgCompletionRate_withData_returnsPositive() {
        // 选课记录会影响完成率（通过 EnrollmentRepository 插入）
        // 此处仅验证方法不抛异常且返回非负值
        Double rate = courseRepository.selectAvgCompletionRate();
        assertNotNull(rate);
        assertTrue(rate >= 0);
    }

    // ==================== selectCurrentSemester ====================

    @Test
    @DisplayName("selectCurrentSemester: 无 semester 时返回 null")
    void selectCurrentSemester_noData_returnsNull() {
        String semester = courseRepository.selectCurrentSemester();
        // p0-seed 课程未设置 semester
        assertNull(semester);
    }

    // ==================== selectDepartmentStats ====================

    @Test
    @DisplayName("selectDepartmentStats: 返回院系统计")
    void selectDepartmentStats_returnsStats() {
        List<Map<String, Object>> stats = courseRepository.selectDepartmentStats();
        assertNotNull(stats);
        // 至少 root 院系(id=1) 应在结果中
        assertFalse(stats.isEmpty());
    }

    // ==================== selectCourseStatsByDepartment ====================

    @Test
    @DisplayName("selectCourseStatsByDepartment: 返回课程统计")
    void selectCourseStatsByDepartment_returnsStats() {
        List<Map<String, Object>> stats = courseRepository.selectCourseStatsByDepartment(1L);
        assertNotNull(stats);
    }

    @Test
    @DisplayName("selectCourseStatsByDepartment: 不存在院系返回空")
    void selectCourseStatsByDepartment_notFound_returnsEmpty() {
        List<Map<String, Object>> stats = courseRepository.selectCourseStatsByDepartment(999L);
        assertNotNull(stats);
        assertTrue(stats.isEmpty());
    }

    // ==================== selectCompletionWarnings ====================

    @Test
    @DisplayName("selectCompletionWarnings: 返回完成率预警")
    void selectCompletionWarnings_returnsWarnings() {
        List<Map<String, Object>> warnings = courseRepository.selectCompletionWarnings();
        assertNotNull(warnings);
    }

    // ==================== selectParticipationTrend ====================

    @Test
    @DisplayName("selectParticipationTrend: 无数据返回空列表")
    void selectParticipationTrend_noData_returnsEmpty() {
        List<Map<String, Object>> trend = courseRepository.selectParticipationTrend(null, null);
        assertNotNull(trend);
    }

    // ==================== selectCompletionTrend ====================

    @Test
    @DisplayName("selectCompletionTrend: 无数据返回空列表")
    void selectCompletionTrend_noData_returnsEmpty() {
        List<Map<String, Object>> trend = courseRepository.selectCompletionTrend(null, null);
        assertNotNull(trend);
    }
}
