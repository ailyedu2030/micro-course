package com.microcourse.repository;

import com.microcourse.entity.LearningProgress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LearningProgressRepository 自定义 SQL 隔离测试。
 * <p>依赖 p0-seed.sql 提供的基础数据：user(id=6 teacher, id=7 student), courses(1,2), chapters(1,5)。</p>
 *
 * <h3>【现象】insertIfAbsent(ON CONFLICT)、SUM/AVG/COUNT DISTINCT 等聚合 SQL 缺乏直接测试</h3>
 * <h3>【根因】进度核心 SQL 含幂等插入和视频分析聚合，变更影响多端学习体验</h3>
 * <h3>【验证】mvn test -Dtest='LearningProgressRepositoryTest' PASS</h3>
 * <h3>【防止再发】所有自定义 SQL 被隔离测试覆盖</h3>
 */
@SpringBootTest
@ActiveProfiles("test")
class LearningProgressRepositoryTest {

    @Autowired
    private LearningProgressRepository learningProgressRepository;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jt;

    @BeforeEach
    void setUp() {
        jt = new JdbcTemplate(dataSource);
        // 全量清理 learning_progress 表测试用数据
        jt.update("DELETE FROM learning_progress");
    }

    // 每次测试用的事务化管理——手动提交不回滚，由 @BeforeEach 清理兜底
    // 但用 @BeforeEach 清理时，同一个测试方法 insert 的数据可能还在。
    // 所以每个测试方法应该在插入后断言，而不依赖跨测试的数据隔离。
    // 每个测试方法内部自己 insert 自己 assert，互不干扰。

    // ==================== insertIfAbsent ====================

    @Test
    @DisplayName("insertIfAbsent: 首次插入成功")
    @Transactional
    void insertIfAbsent_firstInsert_succeeds() {
        LearningProgress lp = createProgress(7L, 1L, 1L, null, 120, 50, false);
        int affected = learningProgressRepository.insertIfAbsent(lp);
        assertEquals(1, affected, "首次插入应成功");
    }

    @Test
    @DisplayName("insertIfAbsent: 重复插入被 ON CONFLICT 阻止")
    @Transactional
    void insertIfAbsent_duplicate_returnsZero() {
        LearningProgress lp = createProgress(7L, 1L, 1L, null, 100, 50, false);
        assertEquals(1, learningProgressRepository.insertIfAbsent(lp), "首次插入应成功");

        LearningProgress duplicate = createProgress(7L, 1L, 1L, null, 200, 100, true);
        int affected = learningProgressRepository.insertIfAbsent(duplicate);
        assertEquals(0, affected, "ON CONFLICT DO NOTHING 应返回 0");
    }

    // ==================== sumTotalWatchTime ====================

    @Test
    @DisplayName("sumTotalWatchTime: 汇总所有观看时长")
    @Transactional
    void sumTotalWatchTime_returnsSum() {
        // 用 JdbcTemplate 直接插入确保不受 ON CONFLICT 影响
        jt.update("INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, " +
                  "video_position, exercise_completed, exercise_passed, total_watch_time, platform, " +
                  "playback_speed, completed, last_watch_at, created_at, updated_at, version) " +
                  "VALUES (?, 1, 1, 0, 50, FALSE, FALSE, ?, 'WEB', 1.0, FALSE, NOW(), NOW(), NOW(), 0)",
                  7L, 120);
        jt.update("INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, " +
                  "video_position, exercise_completed, exercise_passed, total_watch_time, platform, " +
                  "playback_speed, completed, last_watch_at, created_at, updated_at, version) " +
                  "VALUES (?, 1, 1, 0, 50, FALSE, FALSE, ?, 'WEB', 1.0, FALSE, NOW(), NOW(), NOW(), 0)",
                  6L, 180);

        Long sum = learningProgressRepository.sumTotalWatchTime();
        assertNotNull(sum);
        assertEquals(300L, sum, "总观看时长应为 120+180=300");
    }

    @Test
    @DisplayName("sumTotalWatchTime: 无数据返回 0")
    void sumTotalWatchTime_noData_returnsZero() {
        Long sum = learningProgressRepository.sumTotalWatchTime();
        assertNotNull(sum);
        assertEquals(0L, sum);
    }

    // ==================== countUniqueViewersByChapterId ====================

    @Test
    @DisplayName("countUniqueViewersByChapterId: 统计唯一观看人数")
    void countUniqueViewersByChapterId_returnsUniqueCount() {
        jt.update("INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, " +
                  "video_position, total_watch_time, platform, completed, last_watch_at, created_at, updated_at, version) " +
                  "VALUES (?, 1, 1, 0, 50, 100, 'WEB', FALSE, NOW(), NOW(), NOW(), 0)", 7L);
        jt.update("INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, " +
                  "video_position, total_watch_time, platform, completed, last_watch_at, created_at, updated_at, version) " +
                  "VALUES (?, 1, 1, 0, 50, 100, 'WEB', FALSE, NOW(), NOW(), NOW(), 0)", 6L);

        Long count = learningProgressRepository.countUniqueViewersByChapterId(1L);
        assertNotNull(count);
        assertEquals(2L, count);
    }

    @Test
    @DisplayName("countUniqueViewersByChapterId: 无观看返回 0")
    void countUniqueViewersByChapterId_noViewers_returnsZero() {
        Long count = learningProgressRepository.countUniqueViewersByChapterId(999L);
        assertNotNull(count);
        assertEquals(0L, count);
    }

    // ==================== countByChapterId ====================

    @Test
    @DisplayName("countByChapterId: 统计播放次数")
    void countByChapterId_returnsCount() {
        jt.update("INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, " +
                  "video_position, total_watch_time, platform, completed, last_watch_at, created_at, updated_at, version) " +
                  "VALUES (7, 1, 1, 0, 50, 100, 'WEB', FALSE, NOW(), NOW(), NOW(), 0)");

        Long count = learningProgressRepository.countByChapterId(1L);
        assertNotNull(count);
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("countByChapterId: 无记录返回 0")
    void countByChapterId_noData_returnsZero() {
        Long count = learningProgressRepository.countByChapterId(999L);
        assertEquals(0L, count);
    }

    // ==================== avgWatchSecondsByChapterId ====================

    @Test
    @DisplayName("avgWatchSecondsByChapterId: 平均观看时长")
    void avgWatchSecondsByChapterId_returnsAverage() {
        jt.update("INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, " +
                  "video_position, total_watch_time, platform, completed, last_watch_at, created_at, updated_at, version) " +
                  "VALUES (7, 1, 1, 0, 50, 200, 'WEB', FALSE, NOW(), NOW(), NOW(), 0)");
        jt.update("INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, " +
                  "video_position, total_watch_time, platform, completed, last_watch_at, created_at, updated_at, version) " +
                  "VALUES (6, 1, 1, 0, 50, 400, 'WEB', FALSE, NOW(), NOW(), NOW(), 0)");

        Double avg = learningProgressRepository.avgWatchSecondsByChapterId(1L);
        assertNotNull(avg);
        assertEquals(300.0, avg, 0.001, "平均观看时长应为 (200+400)/2=300");
    }

    @Test
    @DisplayName("avgWatchSecondsByChapterId: 无记录返回 0")
    void avgWatchSecondsByChapterId_noData_returnsZero() {
        Double avg = learningProgressRepository.avgWatchSecondsByChapterId(999L);
        assertNotNull(avg);
        assertEquals(0.0, avg, 0.001);
    }

    // ==================== countCompletedByChapterId ====================

    @Test
    @DisplayName("countCompletedByChapterId: 统计完成人数")
    void countCompletedByChapterId_returnsCount() {
        jt.update("INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, " +
                  "video_position, total_watch_time, platform, completed, last_watch_at, created_at, updated_at, version) " +
                  "VALUES (7, 1, 1, 0, 50, 500, 'WEB', TRUE, NOW(), NOW(), NOW(), 0)");

        Long count = learningProgressRepository.countCompletedByChapterId(1L);
        assertNotNull(count);
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("countCompletedByChapterId: 无完成记录返回 0")
    void countCompletedByChapterId_noCompleted_returnsZero() {
        jt.update("INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, " +
                  "video_position, total_watch_time, platform, completed, last_watch_at, created_at, updated_at, version) " +
                  "VALUES (7, 1, 1, 0, 50, 100, 'WEB', FALSE, NOW(), NOW(), NOW(), 0)");

        Long count = learningProgressRepository.countCompletedByChapterId(1L);
        assertEquals(0L, count);
    }

    // ==================== helper ====================

    private LearningProgress createProgress(Long userId, Long courseId, Long chapterId,
                                            Long lessonId, Integer totalWatchTime, int videoPosition,
                                            boolean completed) {
        LearningProgress lp = new LearningProgress();
        lp.setUserId(userId);
        lp.setCourseId(courseId);
        lp.setChapterId(chapterId);
        lp.setSectionId(lessonId);
        lp.setVideoProgress(totalWatchTime != null ? Math.min(totalWatchTime, 100) : 0);
        lp.setVideoPosition(videoPosition);
        lp.setTotalWatchTime(totalWatchTime);
        lp.setExerciseCompleted(false);
        lp.setExercisePassed(false);
        lp.setCompleted(completed);
        lp.setPlatform("WEB");
        lp.setPlaybackSpeed(1.0);
        lp.setConfidence(0);
        lp.setOfflineAttended(false);
        lp.setLastWatchAt(LocalDateTime.now());
        lp.setCreatedAt(LocalDateTime.now());
        lp.setUpdatedAt(LocalDateTime.now());
        lp.setVersion(0);
        return lp;
    }
}
