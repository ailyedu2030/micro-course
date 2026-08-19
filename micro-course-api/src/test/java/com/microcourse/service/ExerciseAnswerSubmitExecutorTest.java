package com.microcourse.service;

import com.microcourse.dto.SubmitAnswerRequest;
import com.microcourse.entity.Exercise;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseQuestionRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.GradeRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.repository.WrongQuestionRepository;
import com.microcourse.service.impl.ExerciseAnswerSubmitExecutor;
import com.microcourse.service.impl.ExerciseAnswerSubmitExecutor.QuestionGrader;
import com.microcourse.service.impl.ExerciseRecordServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ExerciseAnswerSubmitExecutor 单元测试 — 验证 8 步答题流水线的前置校验。
 *
 * <p>PR #254 拆分后,executor 通过构造函数注入 14 个依赖,可独立 Mockito 测试。
 * 本测试聚焦于前置校验路径(纯 Repository 调用,不涉及 MyBatis-Plus entity 反射问题),
 * 完整 happy path 由集成测试覆盖。
 *
 * @author refactor 2026-08-17
 */
@DisplayName("ExerciseAnswerSubmitExecutor 前置校验单元测试")
class ExerciseAnswerSubmitExecutorTest {

    @Mock private ExerciseRepository exerciseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private ExerciseRecordRepository exerciseRecordRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private LearningProgressRepository learningProgressRepository;
    @Mock private ExerciseQuestionRepository exerciseQuestionRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private GradeRepository gradeRepository;
    @Mock private WrongQuestionRepository wrongQuestionRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private NotificationService notificationService;
    @Mock private StringRedisTemplate stringRedisTemplate;

    private ExerciseAnswerSubmitExecutor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        QuestionGrader grader = (q, userAnswer, fullScore) -> null;  // 校验路径不会用到 grader
        executor = new ExerciseAnswerSubmitExecutor(
                exerciseRepository, enrollmentRepository, exerciseRecordRepository,
                videoRepository, learningProgressRepository, exerciseQuestionRepository,
                questionRepository, gradeRepository, wrongQuestionRepository,
                courseRepository, notificationService, stringRedisTemplate,
                new ObjectMapper(), grader);
    }

    @Test
    @DisplayName("练习不存在 → EXERCISE_NOT_FOUND")
    void run_exerciseNotFound() {
        SubmitAnswerRequest req = makeRequest(999L);
        when(exerciseRepository.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(req));
        assertEquals(ErrorCode.EXERCISE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("超过最大答题次数 → BAD_REQUEST_PARAM('已超过最大答题次数')")
    void run_exceedMaxAttempts() {
        Exercise exercise = makeExercise(2, 0, 0);  // maxAttempts=2
        exercise.setId(1L);
        when(exerciseRepository.selectById(1L)).thenReturn(exercise);
        // 已答题次数 = 5 >= maxAttempts(2) → 触发超限
        when(exerciseRecordRepository.selectCount(any())).thenReturn(5L);

        SubmitAnswerRequest req = makeRequest(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(req));
        assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("已超过最大答题次数"));
    }

    @Test
    @DisplayName("考试超时(timeLimit 60分钟)→ EXAM_TIME_EXPIRED")
    void run_examTimeExpired() {
        Exercise exercise = makeExercise(null, 0, 60);  // maxAttempts=0 不限, timeLimit=60min
        exercise.setId(1L);
        when(exerciseRepository.selectById(1L)).thenReturn(exercise);

        SubmitAnswerRequest req = makeRequest(1L);
        req.setDuration(3601);  // 60min*60s+1s, 超过
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(req));
        assertEquals(ErrorCode.EXAM_TIME_EXPIRED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("考试单次提交检查:已有提交记录 → EXAM_ALREADY_SUBMITTED")
    void run_examAlreadySubmitted() {
        Exercise exercise = makeExercise(0, 0, 0);
        exercise.setId(1L);
        exercise.setIsExam(true);
        exercise.setCourseId(null);  // 跳过视频前置检查
        when(exerciseRepository.selectById(1L)).thenReturn(exercise);
        // maxAttempts=0 不限, examCheck 已有 1 条
        when(exerciseRecordRepository.selectCount(any())).thenReturn(1L);

        SubmitAnswerRequest req = makeRequest(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(req));
        assertEquals(ErrorCode.EXAM_ALREADY_SUBMITTED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("maxAttempts=0 表示不限制 → 不应抛'已超过最大答题次数'消息")
    void run_maxAttemptsUnlimited() {
        Exercise exercise = makeExercise(null, 0 /* 不限 */, 0);
        exercise.setId(1L);
        when(exerciseRepository.selectById(1L)).thenReturn(exercise);
        // 即便已有 100 次提交, 也不应抛"超过答题次数"
        when(exerciseRecordRepository.selectCount(any())).thenReturn(100L);
        // 答题题库为空 → 会抛"练习没有题目",但绝不会是"已超过最大答题次数"
        when(exerciseQuestionRepository.selectList(any())).thenReturn(List.of());

        SubmitAnswerRequest req = makeRequest(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(req));
        assertFalse(ex.getMessage().contains("已超过最大答题次数"),
                "maxAttempts=null 时不应抛'超过答题次数',实际错误:" + ex.getMessage());
    }

    @Test
    @DisplayName("timeLimit=0 表示不限制 → 不应抛'答题超时'消息")
    void run_timeLimitUnlimited() {
        Exercise exercise = makeExercise(null, 0, 0 /* timeLimit=0 */);
        exercise.setId(1L);
        when(exerciseRepository.selectById(1L)).thenReturn(exercise);
        when(exerciseQuestionRepository.selectList(any())).thenReturn(List.of());

        SubmitAnswerRequest req = makeRequest(1L);
        req.setDuration(99999);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(req));
        assertFalse(ex.getMessage().contains("答题超时"),
                "timeLimit=0 时不应抛'答题超时',实际错误:" + ex.getMessage());
    }

    // --- helpers ---

    private SubmitAnswerRequest makeRequest(Long exerciseId) {
        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setUserId(100L);
        req.setExerciseId(exerciseId);
        req.setDuration(60);  // 60s
        return req;
    }

    private Exercise makeExercise(Integer maxAttempts, Integer usedAttempts, Integer timeLimitMinutes) {
        Exercise exercise = new Exercise();
        exercise.setMaxAttempts(maxAttempts);
        exercise.setTimeLimit(timeLimitMinutes);
        exercise.setPassScore(60);
        exercise.setTotalScore(100);
        exercise.setIsExam(false);
        return exercise;
    }
}