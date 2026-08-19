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
        // I18-2026-08-17: 委托 {@link ExerciseAnswerSubmitExecutor} 执行 8 步流水线
        ExerciseRecord record = answerSubmitExecutor().run(request);
        Exercise exercise = exerciseRepository.selectById(request.getExerciseId());
        return convertToVO(record, exercise);
    }

    /**
     * I18-2026-08-17: 委托 {@link ExerciseAnswerSubmitExecutor} 执行 8 步答题流水线。
     * 自身保留 {@code GradingResult} 内部类（用作 Jackson JSON 序列化结构体）与
     * {@code gradeQuestion} 公开方法（executor 通过 grader 接口调用）。
     */
    private ExerciseAnswerSubmitExecutor answerSubmitExecutor() {
        return new ExerciseAnswerSubmitExecutor(
                exerciseRepository, enrollmentRepository, exerciseRecordRepository,
                videoRepository, learningProgressRepository, exerciseQuestionRepository,
                questionRepository, gradeRepository, wrongQuestionRepository,
                courseRepository, notificationService, stringRedisTemplate, objectMapper,
                this::gradeQuestion);
    }

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

    GradingResult gradeQuestion(Question question, String userAnswer, Integer fullScore) {
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
    public static class GradingResult {
        Long questionId;
        String questionType;
        String answer;
        Integer score;
        Boolean isCorrect;
        boolean needsManualGrading = false;
    }
}
