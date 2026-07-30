package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.Grade;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.GradeRepository;
import com.microcourse.repository.WrongQuestionRepository;
import com.microcourse.service.ManualGradingService;
import com.microcourse.service.NotificationService;
import com.microcourse.service.ScoreHistoryService;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.XssSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 教师手动批改主观题服务实现。
 * <p>
 * 封装 manualGrade 及其私有辅助方法，与 GradeServiceImpl 共享相同的仓库层依赖。
 * </p>
 */
@Service
public class ManualGradingServiceImpl implements ManualGradingService {

    private static final Logger log = LoggerFactory.getLogger(ManualGradingServiceImpl.class);

    private final GradeRepository gradeRepository;
    private final ExerciseRepository exerciseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final CourseRepository courseRepository;
    private final WrongQuestionRepository wrongQuestionRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final ScoreHistoryService scoreHistoryService;

    public ManualGradingServiceImpl(
            GradeRepository gradeRepository,
            ExerciseRepository exerciseRepository,
            EnrollmentRepository enrollmentRepository,
            ExerciseRecordRepository exerciseRecordRepository,
            CourseRepository courseRepository,
            WrongQuestionRepository wrongQuestionRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            ScoreHistoryService scoreHistoryService) {
        this.gradeRepository = gradeRepository;
        this.exerciseRepository = exerciseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.courseRepository = courseRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.scoreHistoryService = scoreHistoryService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualGrade(Long recordId, Long questionId, Double score, String comment, Long teacherId) {
        ExerciseRecord record = exerciseRecordRepository.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "答题记录不存在");
        }

        Exercise exercise = exerciseRepository.selectById(record.getExerciseId());
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        // SECURITY: 仅课程授课教师或 ADMIN 可批改
        if (exercise.getCourseId() != null) {
            Course course = courseRepository.selectById(exercise.getCourseId());
            if (course != null && !SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权批改该课程练习");
            }
        }

        if (questionId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "questionId 不能为空");
        }
        if (score == null || score < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "score 非法");
        }
        String safeComment = XssSanitizer.sanitize(comment);

        // 解析 answers JSON
        List<Map<String, Object>> items;
        String answers = record.getAnswers();
        if (answers == null || answers.isBlank()) {
            items = new ArrayList<>();
        } else {
            try {
                items = objectMapper.readValue(answers, new TypeReference<List<Map<String, Object>>>() {});
            } catch (JsonProcessingException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "答题数据解析失败");
            }
        }

        // 定位目标题目
        Map<String, Object> target = null;
        for (Map<String, Object> item : items) {
            Long qid = toLong(item.get("questionId"));
            if (qid != null && qid.equals(questionId)) {
                target = item;
                break;
            }
        }
        if (target == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该题目不在此答题记录中");
        }

        // 校验不超过该题满分
        Integer fullScore = toInteger(target.get("fullScore"));
        if (fullScore != null && score > fullScore) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "得分不能超过该题满分");
        }

        // 写回单题 score / comment / 标记已批改
        target.put("score", score);
        target.put("comment", safeComment);
        target.put("isCorrect", score > 0);
        target.put("needsManualGrading", false);

        // 同步错题本：当手动批改判定为错题（score <= 0 或题目判定错误）时，写入 wrong_questions 表
        // 与批改事务一致（同 @Transactional），出错抛异常回滚批改
        boolean isWrong = score <= 0 || (target.get("isCorrect") != null && !Boolean.TRUE.equals(target.get("isCorrect")));
        if (isWrong) {
            try {
                wrongQuestionRepository.upsertWrongQuestion(
                        record.getUserId(),
                        questionId,
                        exercise.getCourseId()
                );
            } catch (Exception e) {
                log.error("[ManualGrading] 同步错题本失败 userId={} questionId={}", record.getUserId(), questionId, e);
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "错题记录同步失败，批改已回滚");
            }
        }

        // 重算记录总得分
        int total = 0;
        for (Map<String, Object> item : items) {
            Integer s = toInteger(item.get("score"));
            if (s != null) {
                total += s;
            }
        }

        // P0 修复: 检查是否所有主观题已批改完毕，若全部已批改则更新 needsManualGrading = false
        boolean allGraded = items.stream().noneMatch(i -> Boolean.TRUE.equals(i.get("needsManualGrading")));

        String newAnswers;
        try {
            newAnswers = objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "答题数据序列化失败");
        }

        boolean passed = exercise.getPassScore() != null && total >= exercise.getPassScore();
        record.setAnswers(newAnswers);
        record.setScore(total);
        record.setPassed(passed);
        record.setNeedsManualGrading(!allGraded);
        // P0: ExerciseRecord 有 @Version，updateById 返回 0 表示版本冲突
        if (exerciseRecordRepository.updateById(record) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "答题记录已被其他操作修改，请刷新后重试");
        }

        // 同步更新 grades 表对应记录（优先按 attemptNo 精确匹配本次作答）
        Grade grade = findGradeForRecord(record, exercise.getCourseId());
        // Phase E: 捕获修改前的原成绩值用于审计
        String manualOldScore = grade != null && grade.getScore() != null ? grade.getScore().toString() : null;
        if (grade != null) {
            grade.setScore(BigDecimal.valueOf(total));
            grade.setTotalScore(record.getTotalScore() != null ? BigDecimal.valueOf(record.getTotalScore()) : null);
            grade.setPassed(passed);
            grade.setAttemptNo(record.getAttemptNo());
            grade.setDuration(record.getDuration());
            grade.setSubmittedAt(record.getSubmittedAt());
            grade.setComment(comment);
            grade.setGradedBy(teacherId);
            grade.setGradedAt(LocalDateTime.now());
            grade.setUpdatedAt(LocalDateTime.now());
            // P0: Grade 有 @Version，updateById 返回 0 表示版本冲突
            if (gradeRepository.updateById(grade) == 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "成绩已被其他操作修改，请刷新后重试");
            }
        } else {
            Grade ng = new Grade();
            ng.setUserId(record.getUserId());
            ng.setExerciseId(record.getExerciseId());
            ng.setCourseId(exercise.getCourseId());
            ng.setScore(BigDecimal.valueOf(total));
            ng.setTotalScore(record.getTotalScore() != null ? BigDecimal.valueOf(record.getTotalScore()) : null);
            ng.setPassed(passed);
            ng.setAttemptNo(record.getAttemptNo());
            ng.setComment(comment);
            ng.setGradedBy(teacherId);
            ng.setGradedAt(LocalDateTime.now());
            ng.setSubmittedAt(record.getSubmittedAt());
            ng.setCreatedAt(LocalDateTime.now());
            ng.setUpdatedAt(LocalDateTime.now());
            // P0: insert 影响行数检查
            if (gradeRepository.insert(ng) == 0) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "成绩记录创建失败，请重试");
            }
        }

        // Phase E: 成绩变更审计追踪
        try {
            Long enrollmentId = findEnrollmentId(exercise.getCourseId(), record.getUserId());
            if (enrollmentId != null) {
                scoreHistoryService.recordChange(enrollmentId, "score",
                    manualOldScore,
                    String.valueOf(total),
                    "MANUAL_GRADE", "手动批改", teacherId);
            }
        } catch (Exception e) {
            log.warn("[Grade] 记录成绩审计失败, 不影响主流程", e);
        }

        // R8 P0-5: 批改后通知学生
        try {
            notificationService.notifyAsync(record.getUserId(), NotificationType.EXERCISE_GRADED,
                    "作业已批改",
                    "教师在练习《" + (exercise != null ? exercise.getTitle() : "") + "》中对您的作答进行了批改，请查看。",
                    exercise.getCourseId());
        } catch (Exception e) {
            log.warn("[Grade] 通知学生练习批改失败 userId={} exerciseId={}", record.getUserId(), record.getExerciseId(), e);
        }
    }

    /**
     * 为某条作答记录定位对应的 grades 行：先按 (user, course, exercise, attempt) 精确匹配，
     * 找不到时退化为同 (user, course, exercise) 下 attempt 最大者。
     */
    private Grade findGradeForRecord(ExerciseRecord record, Long courseId) {
        LambdaQueryWrapper<Grade> exact = new LambdaQueryWrapper<>();
        exact.eq(Grade::getUserId, record.getUserId())
             .eq(Grade::getExerciseId, record.getExerciseId())
             .isNull(Grade::getDeletedAt);
        if (courseId != null) {
            exact.eq(Grade::getCourseId, courseId);
        }
        if (record.getAttemptNo() != null) {
            exact.eq(Grade::getAttemptNo, record.getAttemptNo());
        }
        Grade grade = gradeRepository.selectList(exact).stream().findFirst().orElse(null);
        if (grade != null) {
            return grade;
        }
        LambdaQueryWrapper<Grade> fallback = new LambdaQueryWrapper<>();
        fallback.eq(Grade::getUserId, record.getUserId())
                .eq(Grade::getExerciseId, record.getExerciseId())
                .isNull(Grade::getDeletedAt)
                .orderByDesc(Grade::getAttemptNo);
        if (courseId != null) {
            fallback.eq(Grade::getCourseId, courseId);
        }
        return gradeRepository.selectList(fallback).stream().findFirst().orElse(null);
    }

    /**
     * Phase E: 根据 courseId + userId 查询选课记录 ID。
     * 与 GradeServiceImpl.findEnrollmentId 逻辑相同，独立维护避免循环依赖。
     */
    private Long findEnrollmentId(Long courseId, Long userId) {
        if (courseId == null || userId == null) return null;
        try {
            LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Enrollment::getCourseId, courseId)
                   .eq(Enrollment::getUserId, userId)
                   .isNull(Enrollment::getDeletedAt)
                   .orderByDesc(Enrollment::getEnrolledAt)
                   .last("LIMIT 1");
            Enrollment enrollment = enrollmentRepository.selectOne(wrapper);
            return enrollment != null ? enrollment.getId() : null;
        } catch (Exception e) {
            log.warn("[Grade] 查找 enrollmentId 失败 courseId={} userId={}", courseId, userId, e);
            return null;
        }
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
