package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.SubmitAnswerRequest;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseQuestion;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.Grade;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.Question;
import com.microcourse.entity.Video;
import com.microcourse.entity.WrongQuestion;
import com.microcourse.enums.NotificationType;
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
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 练习答题提交执行器 — 从 {@link ExerciseRecordServiceImpl} 拆出，集中处理：
 * 加载校验 → 批改 → Redis 分布式锁 attemptNo → 插入 exercise_record → 同步 grades →
 * 错题同步 → 学习进度同步 → 教师异步通知 的完整流程。
 *
 * <p>设计目标：
 * <ul>
 *   <li>消除 {@code ExerciseRecordServiceImpl} 中的 800+ 行 ServiceImpl 体积</li>
 *   <li>提供可独立单元测试的纯函数式 API（构造函数注入依赖）</li>
 *   <li>不可变 record 传递上下文快照，避免共享可变状态</li>
 * </ul>
 *
 * @author refactor 2026-08-17
 */
public class ExerciseAnswerSubmitExecutor {

    /**
     * 单题批改函数式接口 — 拆出 grader 后,executor 只需调用该接口,
     * 真正的算法逻辑仍保留在 {@link ExerciseRecordServiceImpl#gradeQuestion} 中。
     */
    @FunctionalInterface
    public interface QuestionGrader {
        ExerciseRecordServiceImpl.GradingResult gradeQuestion(Question question, String userAnswer, Integer fullScore);
    }

    private static final Logger log = LoggerFactory.getLogger(ExerciseAnswerSubmitExecutor.class);

    private final ExerciseRepository exerciseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final VideoRepository videoRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final QuestionRepository questionRepository;
    private final GradeRepository gradeRepository;
    private final WrongQuestionRepository wrongQuestionRepository;
    private final CourseRepository courseRepository;
    private final NotificationService notificationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final QuestionGrader grader;

    public ExerciseAnswerSubmitExecutor(ExerciseRepository exerciseRepository,
                                         EnrollmentRepository enrollmentRepository,
                                         ExerciseRecordRepository exerciseRecordRepository,
                                         VideoRepository videoRepository,
                                         LearningProgressRepository learningProgressRepository,
                                         ExerciseQuestionRepository exerciseQuestionRepository,
                                         QuestionRepository questionRepository,
                                         GradeRepository gradeRepository,
                                         WrongQuestionRepository wrongQuestionRepository,
                                         CourseRepository courseRepository,
                                         NotificationService notificationService,
                                         StringRedisTemplate stringRedisTemplate,
                                         ObjectMapper objectMapper,
                                         QuestionGrader grader) {
        this.exerciseRepository = exerciseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.videoRepository = videoRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.questionRepository = questionRepository;
        this.gradeRepository = gradeRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.courseRepository = courseRepository;
        this.notificationService = notificationService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.grader = grader;
    }

    /**
     * 执行完整答题提交流程：8 步流水线。
     */
    public ExerciseRecord run(SubmitAnswerRequest request) {
        Exercise exercise = loadAndValidateExerciseForSubmit(request);
        SubmitAnswerGradingResult grading = gradeAllAnswers(exercise, request);
        int attemptNo = computeAttemptNoWithRedisLock(request);
        ExerciseRecord record = insertExerciseRecordWithConcurrentGuard(request, exercise, grading, attemptNo);
        insertOrUpdateGradeWithConcurrentGuard(request, exercise, grading, attemptNo);
        syncWrongQuestionsAfterSubmit(request, exercise, grading.gradingResults());
        updateLearningProgressOnPass(request, exercise, grading.passed());
        notifyTeacherOnExerciseSubmit(exercise, grading.totalScore());
        return record;
    }

    /**
     * 步骤 1: 加载练习 + 校验。
     */
    private Exercise loadAndValidateExerciseForSubmit(SubmitAnswerRequest request) {
        Exercise exercise = exerciseRepository.selectById(request.getExerciseId());
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        assertEnrolledForExercise(exercise);
        assertAttemptLimitNotExceeded(exercise, request);
        assertTimeLimitNotExceeded(exercise, request);
        assertExamPrerequisitesMet(exercise, request);
        assertExamNotResubmittable(exercise, request);

        return exercise;
    }

    private void assertEnrolledForExercise(Exercise exercise) {
        if (exercise.getCourseId() == null || SecurityUtil.isAdmin()) {
            return;
        }
        LambdaQueryWrapper<Enrollment> enrollCheck = new LambdaQueryWrapper<>();
        enrollCheck.eq(Enrollment::getUserId, SecurityUtil.getCurrentUserId())
                   .eq(Enrollment::getCourseId, exercise.getCourseId())
                   .in(Enrollment::getEnrollmentStatus, "APPROVED", "COMPLETED")
                   .isNull(Enrollment::getDeletedAt);
        if (enrollmentRepository.selectCount(enrollCheck) == 0) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "未选课不能作答");
        }
    }

    private void assertAttemptLimitNotExceeded(Exercise exercise, SubmitAnswerRequest request) {
        if (exercise.getMaxAttempts() == null || exercise.getMaxAttempts() <= 0) {
            return;
        }
        LambdaQueryWrapper<ExerciseRecord> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(ExerciseRecord::getUserId, request.getUserId())
                   .eq(ExerciseRecord::getExerciseId, request.getExerciseId());
        long attemptCount = exerciseRecordRepository.selectCount(countWrapper);
        if (attemptCount >= exercise.getMaxAttempts()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "已超过最大答题次数");
        }
    }

    private void assertTimeLimitNotExceeded(Exercise exercise, SubmitAnswerRequest request) {
        if (exercise.getTimeLimit() == null || exercise.getTimeLimit() <= 0) {
            return;
        }
        int timeLimitSeconds = exercise.getTimeLimit() * 60;
        if (request.getDuration() != null && request.getDuration() > timeLimitSeconds) {
            throw new BusinessException(ErrorCode.EXAM_TIME_EXPIRED, "答题超时，已超过时间限制");
        }
    }

    private void assertExamPrerequisitesMet(Exercise exercise, SubmitAnswerRequest request) {
        if (!Boolean.TRUE.equals(exercise.getIsExam()) || exercise.getCourseId() == null) {
            return;
        }
        long totalVideosInCourse = videoRepository.selectCount(
            new LambdaQueryWrapper<Video>().eq(Video::getCourseId, exercise.getCourseId()));
        if (totalVideosInCourse == 0) {
            return;
        }
        long completedVideos = learningProgressRepository.selectCount(
            new LambdaQueryWrapper<LearningProgress>()
                .eq(LearningProgress::getUserId, request.getUserId())
                .eq(LearningProgress::getCourseId, exercise.getCourseId())
                .eq(LearningProgress::getCompleted, true));
        if (completedVideos < 1) {
            throw new BusinessException(ErrorCode.PREREQUISITE_NOT_MET,
                "请先观看课程视频后再开始答题");
        }
    }

    private void assertExamNotResubmittable(Exercise exercise, SubmitAnswerRequest request) {
        if (!Boolean.TRUE.equals(exercise.getIsExam())) {
            return;
        }
        LambdaQueryWrapper<ExerciseRecord> examCheckWrapper = new LambdaQueryWrapper<>();
        examCheckWrapper.eq(ExerciseRecord::getUserId, request.getUserId())
                       .eq(ExerciseRecord::getExerciseId, request.getExerciseId());
        long examSubmitCount = exerciseRecordRepository.selectCount(examCheckWrapper);
        if (examSubmitCount > 0) {
            throw new BusinessException(ErrorCode.EXAM_ALREADY_SUBMITTED, "考试已提交，不可重复作答");
        }
    }

    /**
     * 步骤 2: 批改所有答题。
     */
    private SubmitAnswerGradingResult gradeAllAnswers(Exercise exercise, SubmitAnswerRequest request) {
        List<ExerciseQuestion> exerciseQuestions = loadExerciseQuestions(request.getExerciseId());
        Map<Long, ExerciseQuestion> eqMap = buildExerciseQuestionMap(exerciseQuestions);
        Map<Long, Question> questionMap = batchLoadQuestions(request.getAnswers(), eqMap);

        List<ExerciseRecordServiceImpl.GradingResult> gradingResults =
                gradeEachAnswer(request.getAnswers(), eqMap, questionMap);
        int totalScore = gradingResults.stream().mapToInt(r -> r.score).sum();

        int totalPossible = exerciseQuestions.stream()
                .mapToInt(eq -> eq.getScore() == null ? 0 : eq.getScore())
                .sum();
        boolean passed = totalPossible > 0
                && (totalScore * 100.0 / totalPossible) >= exercise.getPassScore();
        boolean hasManualGrading = gradingResults.stream().anyMatch(r -> r.needsManualGrading);
        String answersJson = serializeGradingResults(gradingResults);

        return new SubmitAnswerGradingResult(totalScore, passed, hasManualGrading, gradingResults, answersJson);
    }

    private List<ExerciseQuestion> loadExerciseQuestions(Long exerciseId) {
        LambdaQueryWrapper<ExerciseQuestion> eqWrapper = new LambdaQueryWrapper<>();
        eqWrapper.eq(ExerciseQuestion::getExerciseId, exerciseId)
                .orderByAsc(ExerciseQuestion::getSortOrder);
        List<ExerciseQuestion> exerciseQuestions = exerciseQuestionRepository.selectList(eqWrapper);
        if (exerciseQuestions.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "练习没有题目");
        }
        return exerciseQuestions;
    }

    private Map<Long, ExerciseQuestion> buildExerciseQuestionMap(List<ExerciseQuestion> exerciseQuestions) {
        Map<Long, ExerciseQuestion> eqMap = new HashMap<>();
        for (ExerciseQuestion eq : exerciseQuestions) {
            eqMap.put(eq.getQuestionId(), eq);
        }
        return eqMap;
    }

    private Map<Long, Question> batchLoadQuestions(List<SubmitAnswerRequest.AnswerItem> answerList,
                                                    Map<Long, ExerciseQuestion> eqMap) {
        List<Long> allQuestionIds = answerList.stream()
                .map(SubmitAnswerRequest.AnswerItem::getQuestionId)
                .filter(eqMap::containsKey)
                .collect(Collectors.toList());
        Map<Long, Question> questionMap = new HashMap<>();
        if (!allQuestionIds.isEmpty()) {
            questionRepository.selectBatchIds(allQuestionIds)
                    .forEach(q -> questionMap.put(q.getId(), q));
        }
        return questionMap;
    }

    private List<ExerciseRecordServiceImpl.GradingResult> gradeEachAnswer(List<SubmitAnswerRequest.AnswerItem> answerList,
                                                 Map<Long, ExerciseQuestion> eqMap,
                                                 Map<Long, Question> questionMap) {
        List<ExerciseRecordServiceImpl.GradingResult> gradingResults = new ArrayList<>();
        for (SubmitAnswerRequest.AnswerItem answerItem : answerList) {
            ExerciseQuestion eq = eqMap.get(answerItem.getQuestionId());
            if (eq == null) continue;

            Question question = questionMap.get(answerItem.getQuestionId());
            if (question == null) {
                throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
            }
            gradingResults.add(grader.gradeQuestion(question, answerItem.getAnswer(), eq.getScore()));
        }
        return gradingResults;
    }

    private String serializeGradingResults(List<ExerciseRecordServiceImpl.GradingResult> gradingResults) {
        try {
            return objectMapper.writeValueAsString(gradingResults);
        } catch (JsonProcessingException e) {
            log.error("[ExerciseRecord] JSON 序列化 gradingResults 失败 size={}", gradingResults.size(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "成绩数据序列化失败");
        }
    }

    /**
     * 步骤 3: 计算 attemptNo（Redis 分布式锁）。
     */
    private int computeAttemptNoWithRedisLock(SubmitAnswerRequest request) {
        String lockKey = "attempt:lock:" + request.getUserId() + ":" + request.getExerciseId();
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                    Duration.ofSeconds(5));
            if (!Boolean.TRUE.equals(locked)) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "操作太频繁，请稍后重试");
            }
            try {
                return computeNextAttemptNo(request);
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ExerciseRecord] attemptNo 计算失败,使用默认值 1", e);
            return 1;
        }
    }

    private int computeNextAttemptNo(SubmitAnswerRequest request) {
        QueryWrapper<ExerciseRecord> maxWrapper = new QueryWrapper<>();
        maxWrapper.eq("user_id", request.getUserId())
                .eq("exercise_id", request.getExerciseId())
                .select("COALESCE(MAX(attempt_no), 0) AS max_no");
        Map<String, Object> maxRow = exerciseRecordRepository.selectMaps(maxWrapper).stream()
                .findFirst().orElse(Collections.singletonMap("max_no", 0));
        Object maxVal = maxRow.get("max_no");
        long currentMax = (maxVal instanceof Number n) ? n.longValue() : 0L;
        return (int) currentMax + 1;
    }

    /**
     * 步骤 4: 插入 exercise_record。
     */
    private ExerciseRecord insertExerciseRecordWithConcurrentGuard(SubmitAnswerRequest request,
                                                                  Exercise exercise,
                                                                  SubmitAnswerGradingResult grading,
                                                                  int attemptNo) {
        ExerciseRecord record = buildExerciseRecord(request, exercise, grading, attemptNo);
        try {
            exerciseRecordRepository.insert(record);
            return record;
        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
            log.warn("[ExerciseRecord] 并发 submit 命中 UNIQUE,降级返回已有记录 userId={} exerciseId={} attemptNo={}",
                    request.getUserId(), request.getExerciseId(), attemptNo);
            ExerciseRecord existing = exerciseRecordRepository.selectOne(
                    new LambdaQueryWrapper<ExerciseRecord>()
                            .eq(ExerciseRecord::getUserId, request.getUserId())
                            .eq(ExerciseRecord::getExerciseId, request.getExerciseId())
                            .eq(ExerciseRecord::getAttemptNo, attemptNo));
            if (existing != null) {
                return existing;
            }
            throw dupEx;
        }
    }

    private ExerciseRecord buildExerciseRecord(SubmitAnswerRequest request,
                                               Exercise exercise,
                                               SubmitAnswerGradingResult grading,
                                               int attemptNo) {
        ExerciseRecord record = new ExerciseRecord();
        record.setExerciseId(request.getExerciseId());
        record.setUserId(request.getUserId());
        record.setAttemptNo(attemptNo);
        record.setScore(grading.totalScore());
        record.setTotalScore(exercise.getTotalScore());
        record.setPassed(grading.passed());
        record.setDuration(request.getDuration());
        record.setAnswers(grading.answersJson());
        record.setNeedsManualGrading(grading.hasManualGrading());
        record.setSubmittedAt(LocalDateTime.now());
        return record;
    }

    /**
     * 步骤 5: 同步插入 Grade。
     */
    private void insertOrUpdateGradeWithConcurrentGuard(SubmitAnswerRequest request,
                                                        Exercise exercise,
                                                        SubmitAnswerGradingResult grading,
                                                        int attemptNo) {
        boolean gradeExists = gradeRepository.selectCount(
                new LambdaQueryWrapper<Grade>()
                        .eq(Grade::getUserId, request.getUserId())
                        .eq(Grade::getCourseId, exercise.getCourseId())
                        .eq(Grade::getExerciseId, request.getExerciseId())
                        .eq(Grade::getAttemptNo, attemptNo)) > 0;
        if (gradeExists) {
            return;
        }
        Grade grade = buildGradeFor(request, exercise, grading, attemptNo);
        try {
            gradeRepository.insert(grade);
        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
            log.warn("[Grade] 并发命中唯一约束，幂等忽略 userId={} exerciseId={} attemptNo={}",
                    request.getUserId(), request.getExerciseId(), attemptNo);
        }
    }

    private Grade buildGradeFor(SubmitAnswerRequest request,
                                Exercise exercise,
                                SubmitAnswerGradingResult grading,
                                int attemptNo) {
        Grade grade = new Grade();
        LocalDateTime now = LocalDateTime.now();
        grade.setUserId(request.getUserId());
        grade.setExerciseId(request.getExerciseId());
        grade.setCourseId(exercise.getCourseId());
        grade.setScore(BigDecimal.valueOf(grading.totalScore()));
        grade.setTotalScore(BigDecimal.valueOf(exercise.getTotalScore()));
        grade.setPassed(grading.passed());
        grade.setAttemptNo(attemptNo);
        grade.setDuration(request.getDuration());
        grade.setSubmittedAt(now);
        grade.setGradedAt(now);
        grade.setCreatedAt(now);
        grade.setUpdatedAt(now);
        return grade;
    }

    /**
     * 步骤 6: 错题同步。
     */
    private void syncWrongQuestionsAfterSubmit(SubmitAnswerRequest request,
                                                Exercise exercise,
                                                List<ExerciseRecordServiceImpl.GradingResult> gradingResults) {
        insertWrongQuestions(request, exercise, gradingResults);
        archiveCorrectWrongQuestions(request, gradingResults);
    }

    private void insertWrongQuestions(SubmitAnswerRequest request,
                                      Exercise exercise,
                                      List<ExerciseRecordServiceImpl.GradingResult> gradingResults) {
        Set<Long> wrongQuestionIds = collectWrongQuestionIds(gradingResults);
        if (wrongQuestionIds.isEmpty()) {
            return;
        }
        Set<Long> existingIds = findExistingWrongQuestionIds(request.getUserId(), wrongQuestionIds);
        incrementExistingWrongCount(request.getUserId(), existingIds);
        insertNewWrongQuestions(request, exercise, wrongQuestionIds, existingIds);
    }

    private Set<Long> collectWrongQuestionIds(List<ExerciseRecordServiceImpl.GradingResult> gradingResults) {
        return gradingResults.stream()
                .filter(r -> Boolean.FALSE.equals(r.isCorrect) && r.questionType != null
                        && !r.questionType.equals("SHORT_ANSWER") && !r.questionType.equals("ESSAY"))
                .map(r -> r.questionId)
                .collect(Collectors.toSet());
    }

    private Set<Long> findExistingWrongQuestionIds(Long userId, Set<Long> questionIds) {
        LambdaQueryWrapper<WrongQuestion> existingWQ = new LambdaQueryWrapper<>();
        existingWQ.eq(WrongQuestion::getUserId, userId)
                .in(WrongQuestion::getQuestionId, questionIds);
        return wrongQuestionRepository.selectList(existingWQ).stream()
                .map(WrongQuestion::getQuestionId)
                .collect(Collectors.toSet());
    }

    private void incrementExistingWrongCount(Long userId, Set<Long> existingIds) {
        if (existingIds.isEmpty()) {
            return;
        }
        wrongQuestionRepository.update(null,
                new LambdaUpdateWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, userId)
                        .in(WrongQuestion::getQuestionId, existingIds)
                        .setSql("wrong_count = wrong_count + 1")
                        .setSql("last_wrong_at = NOW()"));
    }

    private void insertNewWrongQuestions(SubmitAnswerRequest request,
                                        Exercise exercise,
                                        Set<Long> wrongQuestionIds,
                                        Set<Long> existingIds) {
        wrongQuestionIds.stream()
                .filter(qid -> !existingIds.contains(qid))
                .forEach(qid -> insertOrIncrementWrongQuestion(request.getUserId(), exercise.getCourseId(), qid));
    }

    private void insertOrIncrementWrongQuestion(Long userId, Long courseId, Long questionId) {
        WrongQuestion wq = new WrongQuestion();
        wq.setUserId(userId);
        wq.setQuestionId(questionId);
        wq.setCourseId(courseId);
        wq.setWrongCount(1);
        wq.setLastWrongAt(LocalDateTime.now());
        wq.setCreatedAt(LocalDateTime.now());
        try {
            wrongQuestionRepository.insert(wq);
        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
            log.debug("[WrongQuestion] 并发命中唯一约束,转为原子累加 userId={} qId={}", userId, questionId);
            wrongQuestionRepository.update(null,
                    new LambdaUpdateWrapper<WrongQuestion>()
                            .eq(WrongQuestion::getUserId, userId)
                            .eq(WrongQuestion::getQuestionId, questionId)
                            .setSql("wrong_count = wrong_count + 1")
                            .setSql("last_wrong_at = NOW()"));
        }
    }

    private void archiveCorrectWrongQuestions(SubmitAnswerRequest request, List<ExerciseRecordServiceImpl.GradingResult> gradingResults) {
        Set<Long> correctQuestionIds = gradingResults.stream()
                .filter(r -> Boolean.TRUE.equals(r.isCorrect) && r.questionId != null)
                .map(r -> r.questionId)
                .collect(Collectors.toSet());
        if (correctQuestionIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<WrongQuestion> correctWQ = new LambdaQueryWrapper<>();
        correctWQ.eq(WrongQuestion::getUserId, request.getUserId())
                 .in(WrongQuestion::getQuestionId, correctQuestionIds);
        List<WrongQuestion> existingCorrectWQ = wrongQuestionRepository.selectList(correctWQ);

        for (WrongQuestion wq : existingCorrectWQ) {
            long newCount = Math.max(0, wq.getWrongCount() - 1);
            if (newCount <= 0) {
                wrongQuestionRepository.deleteById(wq.getId());
            } else {
                wrongQuestionRepository.update(null,
                        new LambdaUpdateWrapper<WrongQuestion>()
                                .eq(WrongQuestion::getId, wq.getId())
                                .set(WrongQuestion::getWrongCount, newCount)
                                .set(WrongQuestion::getLastWrongAt, LocalDateTime.now()));
            }
        }
    }

    /**
     * 步骤 7: 通过则同步学习进度。
     */
    private void updateLearningProgressOnPass(SubmitAnswerRequest request, Exercise exercise, boolean passed) {
        if (!passed || exercise.getCourseId() == null) {
            return;
        }
        LambdaUpdateWrapper<LearningProgress> lpWrapper = new LambdaUpdateWrapper<>();
        lpWrapper.eq(LearningProgress::getUserId, request.getUserId())
                .eq(LearningProgress::getCourseId, exercise.getCourseId())
                .set(LearningProgress::getExercisePassed, true)
                .set(LearningProgress::getUpdatedAt, LocalDateTime.now());
        learningProgressRepository.update(null, lpWrapper);
    }

    /**
     * 步骤 8: 异步通知课程教师。
     */
    private void notifyTeacherOnExerciseSubmit(Exercise exercise, int totalScore) {
        if (exercise.getCourseId() == null) {
            return;
        }
        Course notifyCourse = courseRepository.selectById(exercise.getCourseId());
        if (notifyCourse == null || notifyCourse.getTeacherId() == null) {
            return;
        }
        notificationService.notifyAsync(
                notifyCourse.getTeacherId(),
                NotificationType.EXERCISE_GRADED,
                "学生完成练习",
                "有学生完成了练习《" + exercise.getTitle() + "》，得分 " + totalScore,
                exercise.getId());
    }

    /**
     * 批改结果聚合。
     */
    public record SubmitAnswerGradingResult(
            int totalScore,
            boolean passed,
            boolean hasManualGrading,
            List<ExerciseRecordServiceImpl.GradingResult> gradingResults,
            String answersJson) {}
}