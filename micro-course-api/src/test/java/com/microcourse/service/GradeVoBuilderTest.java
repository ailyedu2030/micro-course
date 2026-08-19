package com.microcourse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.GradeVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.Grade;
import com.microcourse.entity.User;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.GradeVoBuilder;
import com.microcourse.service.impl.GradeVoBuilder.ExerciseRecordKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GradeVoBuilder 单元测试 — 验证批量预加载 + 关联实体填充逻辑。
 *
 * <p>这是 PR #254 拆分 GradeServiceImpl 后的独立可测试入口：
 * 构造函数注入 6 个 Repository,纯 Mockito 即可验证所有 VO 构建路径。
 *
 * @author refactor 2026-08-17
 */
@DisplayName("GradeVoBuilder 批量预加载单元测试")
class GradeVoBuilderTest {

    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private ExerciseRecordRepository exerciseRecordRepository;

    private ObjectMapper objectMapper;
    private GradeVoBuilder builder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        builder = new GradeVoBuilder(
                courseRepository, userRepository, exerciseRepository,
                enrollmentRepository, exerciseRecordRepository, objectMapper);
    }

    @Test
    @DisplayName("空列表:直接返回空 List,不调用任何 Repository")
    void emptyList_noRepoCalls() {
        List<GradeVO> result = builder.buildAll(java.util.List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(courseRepository, userRepository, exerciseRepository,
                enrollmentRepository, exerciseRecordRepository);
    }

    @Test
    @DisplayName("单条成绩:基本字段复制正确")
    void singleGrade_basicFieldsCopied() {
        Grade g = new Grade();
        g.setId(1L);
        g.setCourseId(10L);
        g.setUserId(20L);
        g.setScore(BigDecimal.valueOf(85));
        g.setTotalScore(BigDecimal.valueOf(100));
        g.setPassed(true);
        g.setAttemptNo(1);
        g.setComment("Good");

        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of());

        List<GradeVO> result = builder.buildAll(java.util.List.of(g));

        assertEquals(1, result.size());
        GradeVO vo = result.get(0);
        assertEquals(1L, vo.getId());
        assertEquals(BigDecimal.valueOf(85), vo.getScore());
        assertEquals(true, vo.getPassed());
        assertEquals("Good", vo.getComment());
    }

    @Test
    @DisplayName("批量预加载:相同 userId 在多条 grade 中只查一次")
    void batchLoad_dedupUserIds() {
        Grade g1 = makeGrade(1L, 10L, 20L, 30L, null);
        Grade g2 = makeGrade(2L, 10L, 20L, 31L, null);
        Grade g3 = makeGrade(3L, 11L, 21L, null, null);
        // g1 和 g2 共享 userId=20 + courseId=10
        // g3 是不同的 courseId/userId

        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of());

        builder.buildAll(Arrays.asList(g1, g2, g3));

        // courseIds 应该是 {10, 11}, userIds 应该是 {20, 21}
        verify(courseRepository).selectBatchIds(argThat(set -> set.size() == 2 && set.contains(10L) && set.contains(11L)));
        verify(userRepository).selectBatchIds(argThat(set -> set.size() == 2 && set.contains(20L) && set.contains(21L)));
    }

    @Test
    @DisplayName("gradedBy 字段:作为 userId 收集(用于显示批改人姓名)")
    void gradedByAlsoCollected() {
        Grade g = makeGrade(1L, 10L, 20L, null, 99L);
        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of());

        builder.buildAll(java.util.List.of(g));

        // gradedBy=99 也应该被收集到 userIds 中
        verify(userRepository).selectBatchIds(argThat(set -> set.contains(99L)));
    }

    @Test
    @DisplayName("Course/Exercise/User 关联预加载:Map 正确填充 VO")
    void relatedEntitiesPopulated() {
        Grade g = makeGrade(1L, 10L, 20L, 30L, 99L);  // gradedBy=99

        Course course = new Course();
        course.setId(10L);
        course.setTitle("Java 基础课");

        User student = new User();
        student.setId(20L);
        student.setRealName("张三");

        User grader = new User();
        grader.setId(99L);
        grader.setRealName("李老师");

        Exercise exercise = new Exercise();
        exercise.setId(30L);
        exercise.setTitle("第一章 测试");

        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of(course));
        when(userRepository.selectBatchIds(any())).thenReturn(Arrays.asList(student, grader));
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of(exercise));
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of());

        List<GradeVO> result = builder.buildAll(java.util.List.of(g));

        GradeVO vo = result.get(0);
        assertEquals("Java 基础课", vo.getCourseName());
        assertEquals("张三", vo.getStudentName());
        assertEquals("李老师", vo.getGradedByName());
        assertEquals("第一章 测试", vo.getExerciseTitle());
    }

    @Test
    @DisplayName("User 缺 realName 时回退到 username(避免 NPE)")
    void userDisplayNameFallback() {
        Grade g = makeGrade(1L, 10L, 20L, null, null);

        User student = new User();
        student.setId(20L);
        student.setRealName(null);
        student.setUsername("zhangsan");

        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(java.util.List.of(student));
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of());

        List<GradeVO> result = builder.buildAll(java.util.List.of(g));

        assertEquals("zhangsan", result.get(0).getStudentName());
    }

    @Test
    @DisplayName("enrollmentId 预加载:从嵌套 Map 中正确填充")
    void enrollmentIdMapping() {
        Grade g = makeGrade(1L, 10L, 20L, null, null);

        Enrollment enrollment = new Enrollment();
        enrollment.setId(555L);
        enrollment.setCourseId(10L);
        enrollment.setUserId(20L);

        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of());
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of(enrollment));

        List<GradeVO> result = builder.buildAll(java.util.List.of(g));

        assertEquals(555L, result.get(0).getEnrollmentId());
    }

    @Test
    @DisplayName("ExerciseRecord 关联:按 (userId, exerciseId, attemptNo) 复合键去重")
    void exerciseRecordCompositeKey() {
        Grade g = makeGrade(1L, 10L, 20L, 30L, null);
        g.setAttemptNo(2);

        ExerciseRecord record = new ExerciseRecord();
        record.setId(777L);
        record.setUserId(20L);
        record.setExerciseId(30L);
        record.setAttemptNo(2);
        record.setNeedsManualGrading(true);
        record.setAnswers("[{\"questionId\":1,\"needsManualGrading\":true,\"answer\":\"manual\"}]");

        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of(record));

        List<GradeVO> result = builder.buildAll(java.util.List.of(g));

        GradeVO vo = result.get(0);
        assertEquals(777L, vo.getRecordId());
        assertEquals(true, vo.getNeedsManualGrading());
        assertNotNull(vo.getPendingQuestions());
        assertEquals(1, vo.getPendingQuestions().size());
    }

    @Test
    @DisplayName("ExerciseRecord 关联:不同 attemptNo 不应匹配(防 attemptNo 错位)")
    void exerciseRecordDifferentAttemptNo() {
        Grade g = makeGrade(1L, 10L, 20L, 30L, null);
        g.setAttemptNo(2);

        ExerciseRecord record = new ExerciseRecord();
        record.setId(777L);
        record.setUserId(20L);
        record.setExerciseId(30L);
        record.setAttemptNo(1);  // 不同 attemptNo

        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of(record));

        List<GradeVO> result = builder.buildAll(java.util.List.of(g));

        // attemptNo 不匹配 → recordId 应为 null
        assertNull(result.get(0).getRecordId());
    }

    @Test
    @DisplayName("ExerciseRecord.answers JSON 解析失败仅 warn,不影响整批渲染")
    void exerciseRecordMalformedJsonHandledGracefully() {
        Grade g = makeGrade(1L, 10L, 20L, 30L, null);
        g.setAttemptNo(1);

        ExerciseRecord record = new ExerciseRecord();
        record.setId(777L);
        record.setUserId(20L);
        record.setExerciseId(30L);
        record.setAttemptNo(1);
        record.setNeedsManualGrading(true);
        record.setAnswers("{this is not valid json}");  // 故意构造非法 JSON

        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of(record));

        // 不应抛异常 — 整个 buildAll 仍应正常完成
        List<GradeVO> result = assertDoesNotThrow(() -> builder.buildAll(java.util.List.of(g)));
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("buildSingle:单条 Grade 委托 buildAll")
    void buildSingleDelegatesToBuildAll() {
        Grade g = new Grade();
        g.setId(1L);
        g.setScore(BigDecimal.valueOf(75));

        when(courseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(exerciseRepository.selectBatchIds(any())).thenReturn(java.util.List.of());
        when(enrollmentRepository.selectList(any())).thenReturn(java.util.List.of());
        when(exerciseRecordRepository.selectList(any())).thenReturn(java.util.List.of());

        GradeVO vo = builder.buildSingle(g);

        assertNotNull(vo);
        assertEquals(1L, vo.getId());
        assertEquals(BigDecimal.valueOf(75), vo.getScore());
    }

    // --- helpers ---

    private Grade makeGrade(long id, long courseId, long userId, Long exerciseId, Long gradedBy) {
        Grade g = new Grade();
        g.setId(id);
        g.setCourseId(courseId);
        g.setUserId(userId);
        g.setExerciseId(exerciseId);
        g.setGradedBy(gradedBy);
        g.setScore(BigDecimal.valueOf(80));
        g.setTotalScore(BigDecimal.valueOf(100));
        g.setPassed(true);
        g.setAttemptNo(1);
        g.setCreatedAt(LocalDateTime.now());
        return g;
    }
}