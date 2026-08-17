package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.SubmitAnswerRequest;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.Course;
import com.microcourse.entity.ExerciseQuestion;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.Grade;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.Question;
import com.microcourse.entity.Video;
import com.microcourse.entity.WrongQuestion;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseQuestionRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.GradeRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.repository.WrongQuestionRepository;
import com.microcourse.service.ExerciseRecordService;
import com.microcourse.service.NotificationService;
import com.microcourse.enums.NotificationType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExerciseRecordServiceImpl implements ExerciseRecordService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseRecordServiceImpl.class);

    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final QuestionRepository questionRepository;
    private final WrongQuestionRepository wrongQuestionRepository;
    private final GradeRepository gradeRepository;
    private final ObjectMapper objectMapper;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;
    private final VideoRepository videoRepository;
    private final LearningProgressRepository learningProgressRepository;
    /** P0-05: 答题 attemptNo 分布式锁 */
    private final StringRedisTemplate stringRedisTemplate;

    public ExerciseRecordServiceImpl(ExerciseRecordRepository exerciseRecordRepository,
                                        ExerciseRepository exerciseRepository,
                                        ExerciseQuestionRepository exerciseQuestionRepository,
                                        QuestionRepository questionRepository,
                                        WrongQuestionRepository wrongQuestionRepository,
                                        GradeRepository gradeRepository,
                                        ObjectMapper objectMapper,
                                        CourseRepository courseRepository,
                                        EnrollmentRepository enrollmentRepository,
                                        NotificationService notificationService,
                                        VideoRepository videoRepository,
                                        LearningProgressRepository learningProgressRepository,
                                        StringRedisTemplate stringRedisTemplate) {
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.questionRepository = questionRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.gradeRepository = gradeRepository;
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationService = notificationService;
        this.videoRepository = videoRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExerciseRecordVO submitAnswer(SubmitAnswerRequest request) {
        // 1. 加载练习 + 前置校验（选课/超时/答题次数/视频进度/考试单次）
        Exercise exercise = loadAndValidateExerciseForSubmit(request);

        // 2. 加载题目并批改（N+1→1+1 批量预加载）
        SubmitAnswerGradingResult grading = gradeAllAnswers(exercise, request);

        // 3. 计算 attemptNo（Redis 分布式锁保证原子递增）
        int attemptNo = computeAttemptNoWithRedisLock(request);

        // 4. 插入 exercise_record（并发兜底：命中 UK 降级返回已有记录）
        ExerciseRecord record = insertExerciseRecordWithConcurrentGuard(request, exercise, grading, attemptNo);

        // 5. 同步 grades 表（并发兜底：预检查 + DuplicateKey 幂等忽略）
        insertOrUpdateGradeWithConcurrentGuard(request, exercise, grading, attemptNo);

        // 6. 错题入库（客观题）+ 错题归档（答对的错题 wrong_count-1）
        syncWrongQuestionsAfterSubmit(request, exercise, grading.gradingResults());

        // 7. 通过则同步学习进度
        updateLearningProgressOnPass(request, exercise, grading.passed());

        // 8. 异步通知教师（不阻塞答题主流程）
        notifyTeacherOnExerciseSubmit(exercise, grading.totalScore());

        return convertToVO(record, exercise);
    }

    /**
     * 加载练习 + 校验（步骤 1）。
     * 任何前置校验失败都抛 BusinessException，事务回滚。
     */
    private Exercise loadAndValidateExerciseForSubmit(SubmitAnswerRequest request) {
        Exercise exercise = exerciseRepository.selectById(request.getExerciseId());
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        // R12 P0-1: 选课检查 — 学生只能提交已选课程课件的练习
        assertEnrolledForExercise(exercise);

        // 答题次数 / 视频进度 / 考试单次提交 等校验
        assertAttemptLimitNotExceeded(exercise, request);
        assertTimeLimitNotExceeded(exercise, request);
        assertExamPrerequisitesMet(exercise, request);
        assertExamNotResubmittable(exercise, request);

        return exercise;
    }

    /**
     * R12 P0-1: 学生只能提交已选课程课件的练习（管理员除外）。
     */
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

    /**
     * 后端独立查询答题次数，不依赖前端 attemptNo。
     */
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

    /**
     * 后端独立检查答题时长，不依赖客户端计时。
     */
    private void assertTimeLimitNotExceeded(Exercise exercise, SubmitAnswerRequest request) {
        if (exercise.getTimeLimit() == null || exercise.getTimeLimit() <= 0) {
            return;
        }
        int timeLimitSeconds = exercise.getTimeLimit() * 60;
        if (request.getDuration() != null && request.getDuration() > timeLimitSeconds) {
            throw new BusinessException(ErrorCode.EXAM_TIME_EXPIRED, "答题超时，已超过时间限制");
        }
    }

    /**
     * 考试前置门槛：必须先观看过至少 1 个课程视频（仅当课程有视频时检查）。
     * P1-C (2026-08-04): 随堂练习豁免，避免"先看视频"拦截学生巩固学习；考试保持门槛（防作弊）。
     */
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

    /**
     * P1C-025: 考试只能提交一次，不可重做。
     */
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
     * 批改所有答题（步骤 2）。返回聚合结果：总分 / 通过 / 是否需人工批改 / 各题批改明细。
     */
    private SubmitAnswerGradingResult gradeAllAnswers(Exercise exercise, SubmitAnswerRequest request) {
        List<ExerciseQuestion> exerciseQuestions = loadExerciseQuestions(request.getExerciseId());
        Map<Long, ExerciseQuestion> eqMap = buildExerciseQuestionMap(exerciseQuestions);
        Map<Long, Question> questionMap = batchLoadQuestions(request.getAnswers(), eqMap);

        List<GradingResult> gradingResults = gradeEachAnswer(request.getAnswers(), eqMap, questionMap);
        int totalScore = gradingResults.stream().mapToInt(r -> r.score).sum();

        int totalPossible = exerciseQuestions.stream()
                .mapToInt(eq -> eq.getScore() == null ? 0 : eq.getScore())
                .sum();
        // 2026-08-04 修复：pass_score 数据字典定义为「及格分（百分制）」，
        // 原实现按绝对分值比较（totalScore >= passScore），小分值练习（如 2 题共 20 分）
        // 永远无法达到 60 及格线 → 学生永远「未通过」。
        // 改为得分率比较：totalScore / totalPossible * 100 >= passScore。
        boolean passed = totalPossible > 0
                && (totalScore * 100.0 / totalPossible) >= exercise.getPassScore();
        boolean hasManualGrading = gradingResults.stream().anyMatch(r -> r.needsManualGrading);
        String answersJson = serializeGradingResults(gradingResults);

        return new SubmitAnswerGradingResult(totalScore, passed, hasManualGrading, gradingResults, answersJson);
    }

    /**
     * 加载练习的所有题目（按 sortOrder 升序）。
     */
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

    /**
     * 构造 questionId -> ExerciseQuestion 的 O(1) 查找 Map。
     */
    private Map<Long, ExerciseQuestion> buildExerciseQuestionMap(List<ExerciseQuestion> exerciseQuestions) {
        Map<Long, ExerciseQuestion> eqMap = new HashMap<>();
        for (ExerciseQuestion eq : exerciseQuestions) {
            eqMap.put(eq.getQuestionId(), eq);
        }
        return eqMap;
    }

    /**
     * 批量预加载所有 Question（N+1→1+1 优化），过滤掉不在本练习中的 questionId。
     */
    private Map<Long, Question> batchLoadQuestions(List<SubmitAnswerRequest.AnswerItem> answerList,
                                                    Map<Long, ExerciseQuestion> eqMap) {
        List<Long> allQuestionIds = answerList.stream()
                .map(SubmitAnswerRequest.AnswerItem::getQuestionId)
                .filter(eqMap::containsKey)
                .collect(java.util.stream.Collectors.toList());
        Map<Long, Question> questionMap = new HashMap<>();
        if (!allQuestionIds.isEmpty()) {
            questionRepository.selectBatchIds(allQuestionIds)
                    .forEach(q -> questionMap.put(q.getId(), q));
        }
        return questionMap;
    }

    /**
     * 逐题批改：跳过不在练习中的题（前端可能多传），缺失题目抛 QUESTION_NOT_FOUND。
     */
    private List<GradingResult> gradeEachAnswer(List<SubmitAnswerRequest.AnswerItem> answerList,
                                                 Map<Long, ExerciseQuestion> eqMap,
                                                 Map<Long, Question> questionMap) {
        List<GradingResult> gradingResults = new ArrayList<>();
        for (SubmitAnswerRequest.AnswerItem answerItem : answerList) {
            ExerciseQuestion eq = eqMap.get(answerItem.getQuestionId());
            if (eq == null) continue;

            Question question = questionMap.get(answerItem.getQuestionId());
            if (question == null) {
                throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
            }
            gradingResults.add(gradeQuestion(question, answerItem.getAnswer(), eq.getScore()));
        }
        return gradingResults;
    }

    /**
     * 序列化批改结果为 JSON（写入 exercise_record.answers）。
     */
    private String serializeGradingResults(List<GradingResult> gradingResults) {
        try {
            return objectMapper.writeValueAsString(gradingResults);
        } catch (JsonProcessingException e) {
            log.error("[ExerciseRecord] JSON 序列化 gradingResults 失败 size={}", gradingResults.size(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "成绩数据序列化失败");
        }
    }

    /**
     * 计算 attemptNo（步骤 3）：Redis 分布式锁保证原子递增，防止并发竞态。
     * P0-05 修复：极端情况降级到默认值 1（避免 Redis 故障导致整事务回滚）。
     */
    private int computeAttemptNoWithRedisLock(SubmitAnswerRequest request) {
        String lockKey = "attempt:lock:" + request.getUserId() + ":" + request.getExerciseId();
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                    java.time.Duration.ofSeconds(5));
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

    /**
     * 实际计算下一个 attemptNo：取 MAX(attempt_no) + 1。
     */
    private int computeNextAttemptNo(SubmitAnswerRequest request) {
        QueryWrapper<ExerciseRecord> maxWrapper = new QueryWrapper<>();
        maxWrapper.eq("user_id", request.getUserId())
                .eq("exercise_id", request.getExerciseId())
                .select("COALESCE(MAX(attempt_no), 0) AS max_no");
        Map<String, Object> maxRow = exerciseRecordRepository.selectMaps(maxWrapper).stream()
                .findFirst().orElse(java.util.Collections.singletonMap("max_no", 0));
        Object maxVal = maxRow.get("max_no");
        long currentMax = (maxVal instanceof Number n) ? n.longValue() : 0L;
        return (int) currentMax + 1;
    }

    /**
     * 插入 exercise_record（步骤 4）。DA-1 修复：并发 submit 命中 UNIQUE 时降级返回已有记录。
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

    /**
     * 构造 ExerciseRecord 实体（不持久化）。
     */
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
     * 同步插入 Grade（步骤 5）。Round 8-4 修复：UK 预检查 + DuplicateKey 幂等忽略。
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

    /**
     * 构造 Grade 实体。
     */
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
     * 错题同步（步骤 6）：
     * - 答错的客观题 → 错题表（增量更新或新增）
     * - 答对且之前在错题表 → 错题归档（wrong_count - 1，归零则删除）
     */
    private void syncWrongQuestionsAfterSubmit(SubmitAnswerRequest request,
                                                Exercise exercise,
                                                List<GradingResult> gradingResults) {
        insertWrongQuestions(request, exercise, gradingResults);
        archiveCorrectWrongQuestions(request, gradingResults);
    }

    /**
     * 答错的客观题入库（主观题 SHORT_ANSWER/ESSAY 需人工批改，不入错题表）。
     */
    private void insertWrongQuestions(SubmitAnswerRequest request,
                                      Exercise exercise,
                                      List<GradingResult> gradingResults) {
        Set<Long> wrongQuestionIds = collectWrongQuestionIds(gradingResults);
        if (wrongQuestionIds.isEmpty()) {
            return;
        }
        Set<Long> existingIds = findExistingWrongQuestionIds(request.getUserId(), wrongQuestionIds);
        incrementExistingWrongCount(request.getUserId(), existingIds);
        insertNewWrongQuestions(request, exercise, wrongQuestionIds, existingIds);
    }

    /**
     * 抽取错题 ID：客观题且答错（排除主观题 SHORT_ANSWER/ESSAY）。
     */
    private Set<Long> collectWrongQuestionIds(List<GradingResult> gradingResults) {
        return gradingResults.stream()
                .filter(r -> Boolean.FALSE.equals(r.isCorrect) && r.questionType != null
                        && !r.questionType.equals("SHORT_ANSWER") && !r.questionType.equals("ESSAY"))
                .map(r -> r.questionId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 批量查询已存在的错题。
     */
    private Set<Long> findExistingWrongQuestionIds(Long userId, Set<Long> questionIds) {
        LambdaQueryWrapper<WrongQuestion> existingWQ = new LambdaQueryWrapper<>();
        existingWQ.eq(WrongQuestion::getUserId, userId)
                .in(WrongQuestion::getQuestionId, questionIds);
        return wrongQuestionRepository.selectList(existingWQ).stream()
                .map(WrongQuestion::getQuestionId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 已存在错题 → wrong_count + 1（原子 SET SQL）。
     */
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

    /**
     * 新错题 → 单条插入，捕获并发 UK 冲突降级为原子累加。CON-005 修复。
     */
    private void insertNewWrongQuestions(SubmitAnswerRequest request,
                                        Exercise exercise,
                                        Set<Long> wrongQuestionIds,
                                        Set<Long> existingIds) {
        wrongQuestionIds.stream()
                .filter(qid -> !existingIds.contains(qid))
                .forEach(qid -> insertOrIncrementWrongQuestion(request.getUserId(), exercise.getCourseId(), qid));
    }

    /**
     * 插入单条错题；并发兜底：捕获 DuplicateKey 转为 UPDATE +1。
     */
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

    /**
     * P1-C 答对归档：之前在错题表中、现在答对的题 → wrong_count - 1，归零删除。
     */
    private void archiveCorrectWrongQuestions(SubmitAnswerRequest request, List<GradingResult> gradingResults) {
        Set<Long> correctQuestionIds = gradingResults.stream()
                .filter(r -> Boolean.TRUE.equals(r.isCorrect) && r.questionId != null)
                .map(r -> r.questionId)
                .collect(java.util.stream.Collectors.toSet());
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
     * P1C-023: 练习通过则同步 learning_progress.exercise_passed = true。
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
     * Phase B-2 (P0-7): 异步通知课程教师。
     * Exercise 无 teacherId 字段，经 courseId → course → teacherId 解析；@Async 不阻塞答题主流程。
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
     * 批改结果聚合 — {@link #submitAnswer} 步骤 2/4 之间传递的不可变快照。
     */
    private record SubmitAnswerGradingResult(
            int totalScore,
            boolean passed,
            boolean hasManualGrading,
            List<GradingResult> gradingResults,
            String answersJson) {}

    private String normalizeQuestionType(String type) {
        if (type == null) return null;
        return switch (type) {
            case "SINGLE_CHOICE" -> "SINGLE";
            case "MULTIPLE_CHOICE" -> "MULTIPLE";
            case "FILL_BLANK" -> "FILL";
            default -> type;
        };
    }

    /**
     * 多选题答案解析：兼容前端提交的 JSON 数组（["2","4"]）与纯逗号分隔（"2,4"）两种格式。
     * P1-C 修复：此前 JSON 数组经 split(",") 后元素带引号，导致多选题答案永远不匹配被判错。
     */
    private Set<String> parseMultipleAnswerSet(String raw) {
        Set<String> set = new java.util.HashSet<>();
        if (raw == null || raw.isBlank()) return set;
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<String> list = mapper.readValue(trimmed,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
                for (String v : list) {
                    if (v != null && !v.isBlank()) set.add(v.trim().toUpperCase());
                }
                return set;
            } catch (Exception ignored) {
                // JSON 解析失败则回退到逗号分隔
            }
        }
        for (String v : trimmed.split(",")) {
            String s = v.trim();
            if (!s.isEmpty()) set.add(s.toUpperCase());
        }
        return set;
    }

    private GradingResult gradeQuestion(Question question, String userAnswer, Integer fullScore) {
        GradingResult result = new GradingResult();
        result.questionId = question.getId();
        result.questionType = question.getQuestionType();
        result.answer = userAnswer;

        if (userAnswer == null) {
            result.score = 0;
            result.isCorrect = false;
            return result;
        }

        String correctAnswer = question.getAnswer();
        boolean isCorrect;

        // P0 防御: correctAnswer 为 null 时视为未设置标准答案,标记为待人工批改
        if (correctAnswer == null) {
            result.score = 0;
            result.isCorrect = null;
            result.needsManualGrading = true;
            return result;
        }

        String qType = normalizeQuestionType(question.getQuestionType());
        switch (qType) {
            case "SINGLE":
                // 单选题：直接字符串对比
                isCorrect = userAnswer.trim().equals(correctAnswer.trim());
                break;
            case "MULTIPLE":
                // P0: 改为部分得分制 — 选对比例×满分
                {
                    Set<String> corrects = new java.util.HashSet<>(java.util.Arrays.asList(
                        correctAnswer != null ? correctAnswer.toUpperCase().split(",") : new String[0]));
                    Set<String> userAnsSet = parseMultipleAnswerSet(userAnswer);
                    if (corrects.equals(userAnsSet)) {
                        result.isCorrect = true;
                        result.score = fullScore;
                    } else if (userAnsSet.isEmpty() || userAnsSet.size() > corrects.size() * 2) {
                        result.score = 0;
                        result.isCorrect = false;
                    } else {
                        long correctCount = userAnsSet.stream().filter(corrects::contains).count();
                        long wrongCount = userAnsSet.stream().filter(a -> !corrects.contains(a)).count();
                        double ratio = (double)(correctCount - wrongCount) / corrects.size();
                        result.score = (int)Math.round(Math.max(0, ratio) * fullScore);
                        result.isCorrect = false;
                    }
                    return result;
                }
            case "JUDGE":
                // 判断题：直接对比
                isCorrect = userAnswer.trim().equals(correctAnswer.trim());
                break;
            case "FILL":
                if (correctAnswer == null || correctAnswer.trim().isEmpty()) {
                    result.score = 0;
                    result.isCorrect = false;
                    result.needsManualGrading = true;
                    return result;
                } else {
                    String ua = userAnswer.trim().replaceAll("[\\s,，;；。、]+", " ").trim();
                    String ca = correctAnswer.trim().replaceAll("[\\s,，;；。、]+", " ").trim();
                    // 嘗試數值比較(容差5%)
                    try {
                        double numUser = Double.parseDouble(ua);
                        double numCorrect = Double.parseDouble(ca);
                        if (Math.abs(numUser - numCorrect) / Math.max(1.0, Math.abs(numCorrect)) <= 0.05) {
                            result.isCorrect = true;
                            result.score = fullScore;
                        } else {
                            result.score = 0;
                            result.isCorrect = false;
                        }
                    } catch (NumberFormatException e) {
                        // 文本比較:忽略大小寫+無視連續空格+忽略標點
                        String uaNorm = ua.replaceAll("[^\\p{L}\\p{N}]+", "").toLowerCase();
                        String caNorm = ca.replaceAll("[^\\p{L}\\p{N}]+", "").toLowerCase();
                        result.isCorrect = uaNorm.equals(caNorm);
                        result.score = result.isCorrect ? fullScore : 0;
                    }
                    return result;
                }
            case "SHORT_ANSWER":
            case "ESSAY":
                // 简答/论述：标记为待人工批改
                result.score = 0;
                result.isCorrect = null;
                result.needsManualGrading = true;
                return result;
            default:
                isCorrect = false;
        }

        result.isCorrect = isCorrect;
        result.score = isCorrect ? fullScore : 0;
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExerciseRecordVO> getRecordsByExercise(Long exerciseId) {
        Exercise exercise = exerciseRepository.selectById(exerciseId);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }
        // R12 P0-2: 教师仅能查看自己授课课程的学生答题记录
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            if (exercise.getCourseId() != null) {
                Course c = courseRepository.selectById(exercise.getCourseId());
                if (c != null && !SecurityUtil.isOwnerOrAdmin(c.getTeacherId())) {
                    throw new BusinessException(ErrorCode.NO_PERMISSION);
                }
            }
        }

        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseRecord::getExerciseId, exerciseId)
                .orderByDesc(ExerciseRecord::getSubmittedAt)
                .last("LIMIT 1000");
        List<ExerciseRecord> records = exerciseRecordRepository.selectList(wrapper);

        return records.stream()
                .map(r -> convertToVO(r, exercise))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExerciseRecordVO> getMyRecords(Long userId, Long exerciseId) {
        Exercise exercise = exerciseRepository.selectById(exerciseId);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseRecord::getUserId, userId)
                .eq(ExerciseRecord::getExerciseId, exerciseId)
                .orderByDesc(ExerciseRecord::getSubmittedAt);
        List<ExerciseRecord> records = exerciseRecordRepository.selectList(wrapper);

        return records.stream()
                .map(r -> convertToVO(r, exercise))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExerciseRecordVO getRecordById(Long id, Long userId) {
        ExerciseRecord record = exerciseRecordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "答题记录不存在");
        }
        // P1-I-006: ADMIN/TEACHER/ACADEMIC 可查看任意学生的答题记录, 普通学生只能看自己
        // 之前: 只允许 record 本人查询, 导致教师后台"答题详情"页面无法打开学生答题记录
        boolean isOwner = record.getUserId().equals(userId);
        boolean isPrivileged = SecurityUtil.isAdmin()
                || SecurityUtil.hasRole("TEACHER")
                || SecurityUtil.hasRole("ACADEMIC");
        if (!isOwner && !isPrivileged) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        Exercise exercise = exerciseRepository.selectById(record.getExerciseId());
        return convertToVO(record, exercise);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAccuracyTrend(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseRecord::getUserId, userId)
               .ge(ExerciseRecord::getSubmittedAt, since)
               .orderByAsc(ExerciseRecord::getSubmittedAt);
        // 使用 MyBatis-Plus 分页代替 LIMIT 2000，防止 OOM
        Page<ExerciseRecord> pg = new Page<>(1, com.microcourse.constants.ApiConstants.STATS_LIMIT);
        List<ExerciseRecord> records = exerciseRecordRepository.selectPage(pg, wrapper).getRecords();

        // P0 修复: 基于逐题 isCorrect 统计正确率，而非基于整卷 passed
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<LocalDate, int[]> byDate = new TreeMap<>();  // [totalQ, correctQ]
        for (ExerciseRecord r : records) {
            if (r.getSubmittedAt() == null) continue;
            LocalDate date = r.getSubmittedAt().toLocalDate();
            int[] counter = byDate.computeIfAbsent(date, k -> new int[2]);
            String answers = r.getAnswers();
            if (answers != null && !answers.isBlank()) {
                try {
                    List<Map<String, Object>> items = objectMapper.readValue(answers, new TypeReference<List<Map<String, Object>>>() {});
                    for (Map<String, Object> item : items) {
                        counter[0]++;
                        if (Boolean.TRUE.equals(item.get("isCorrect"))) {
                            counter[1]++;
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.warn("JSON解析失败: {}", e.getMessage());
                    // 损坏的 answers JSON，按单题 0/1 处理（即整卷算 0 题 0 对）
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, int[]> e : byDate.entrySet()) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", e.getKey().format(formatter));
            int total = e.getValue()[0];
            int correct = e.getValue()[1];
            dayData.put("totalCount", total);
            dayData.put("correctCount", correct);
            dayData.put("accuracy", total == 0 ? 0.0 : Math.round((double) correct * 10000.0 / total) / 10000.0);
            result.add(dayData);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public int getAttemptCount(Long userId, Long exerciseId) {
        QueryWrapper<ExerciseRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("exercise_id", exerciseId)
                .select("COALESCE(MAX(attempt_no), 0) AS max_no");
        Map<String, Object> row = exerciseRecordRepository.selectMaps(wrapper).stream()
                .findFirst().orElse(Collections.singletonMap("max_no", 0));
        Object maxVal = row.get("max_no");
        return (maxVal instanceof Number n) ? n.intValue() : 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAttemptSummary(Long userId, Long exerciseId) {
        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseRecord::getUserId, userId)
               .eq(ExerciseRecord::getExerciseId, exerciseId);
        List<ExerciseRecord> records = exerciseRecordRepository.selectList(wrapper);
        boolean passed = records.stream().anyMatch(r -> Boolean.TRUE.equals(r.getPassed()));
        ExerciseRecord latest = records.stream()
                .max(java.util.Comparator.comparing(ExerciseRecord::getSubmittedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("attemptCount", records.size());
        result.put("passed", passed);
        result.put("score", latest != null ? latest.getScore() : null);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExerciseRecordVO> getResult(Long exerciseId, Long currentUserId) {
        // STUDENT（非 ADMIN）：仅返回本人答题记录
        if (SecurityUtil.hasRole("STUDENT") && !SecurityUtil.isAdmin()) {
            return getMyRecords(currentUserId, exerciseId);
        }
        // TEACHER / ADMIN：返回该练习全部答题记录
        return getRecordsByExercise(exerciseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAnalytics(Long exerciseId) {
        List<ExerciseRecordVO> records = getRecordsByExercise(exerciseId);
        int totalAttempts = records.size();
        long passedCount = records.stream()
                .filter(r -> Boolean.TRUE.equals(r.getPassed()))
                .count();
        long participantCount = records.stream()
                .map(ExerciseRecordVO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        double avgScore = records.stream()
                .filter(r -> r.getScore() != null)
                .mapToInt(ExerciseRecordVO::getScore)
                .average()
                .orElse(0.0);

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("exerciseId", exerciseId);
        analytics.put("totalAttempts", totalAttempts);
        analytics.put("participantCount", participantCount);
        analytics.put("passedCount", passedCount);
        analytics.put("passRate", totalAttempts > 0 ? (double) passedCount / totalAttempts : 0.0);
        analytics.put("avgScore", avgScore);
        return analytics;
    }

    private ExerciseRecordVO convertToVO(ExerciseRecord record, Exercise exercise) {
        ExerciseRecordVO vo = new ExerciseRecordVO();
        vo.setId(record.getId());
        vo.setExerciseId(record.getExerciseId());
        vo.setExerciseTitle(exercise != null ? exercise.getTitle() : null);
        vo.setUserId(record.getUserId());
        vo.setAttemptNo(record.getAttemptNo());
        vo.setScore(record.getScore());
        vo.setTotalScore(record.getTotalScore());
        vo.setPassed(record.getPassed());
        vo.setDuration(record.getDuration());
        vo.setAnswers(record.getAnswers());
        vo.setNeedsManualGrading(record.getNeedsManualGrading());
        vo.setSubmittedAt(record.getSubmittedAt());
        return vo;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class GradingResult {
        Long questionId;
        String questionType;
        String answer;
        Integer score;
        Boolean isCorrect;
        boolean needsManualGrading = false;
    }
}
