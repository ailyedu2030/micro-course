package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.GradeCreateRequest;
import com.microcourse.dto.GradeTeacherSubmitRequest;
import com.microcourse.dto.GradeUpdateRequest;
import com.microcourse.dto.GradeVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.Grade;
import com.microcourse.entity.User;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.GradeRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.enums.NotificationType;
import com.microcourse.service.GradeService;
import com.microcourse.service.ManualGradingService;
import com.microcourse.service.NotificationService;
import com.microcourse.service.ScoreHistoryService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GradeServiceImpl implements GradeService {

    private static final Logger log = LoggerFactory.getLogger(GradeServiceImpl.class);

    private final GradeRepository gradeRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final ScoreHistoryService scoreHistoryService;
    private final ManualGradingService manualGradingService;

    public GradeServiceImpl(
            GradeRepository gradeRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            ExerciseRepository exerciseRepository,
            EnrollmentRepository enrollmentRepository,
            ExerciseRecordRepository exerciseRecordRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            ScoreHistoryService scoreHistoryService,
            ManualGradingService manualGradingService) {
        this.gradeRepository = gradeRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.scoreHistoryService = scoreHistoryService;
        this.manualGradingService = manualGradingService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<GradeVO> page(Long courseId, Long studentId, int page, int size) {
        LambdaQueryWrapper<Grade> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Grade::getCourseId, courseId);
        }
        if (studentId != null) {
            wrapper.eq(Grade::getUserId, studentId);
        }

        // P0-9: TEACHER 数据隔离 — 只能看到自己授课课程的成绩
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            Long currentUserId = SecurityUtil.getCurrentUserId();
            LambdaQueryWrapper<Course> courseWrapper = new LambdaQueryWrapper<>();
            courseWrapper.eq(Course::getTeacherId, currentUserId).isNull(Course::getDeletedAt);
            List<Course> teacherCourses = courseRepository.selectList(courseWrapper);
            List<Long> teacherCourseIds = teacherCourses.stream().map(Course::getId).collect(Collectors.toList());
            if (teacherCourseIds.isEmpty()) {
                return PageResult.of(new ArrayList<>(), 0L, page, size);
            }
            wrapper.in(Grade::getCourseId, teacherCourseIds);
        }

        wrapper.isNull(Grade::getDeletedAt).orderByDesc(Grade::getCreatedAt);

        IPage<Grade> gradePage = gradeRepository.selectPage(new Page<>(page + 1, size), wrapper);

        List<GradeVO> vos = batchConvertToVO(gradePage.getRecords());
        return PageResult.of(vos, gradePage.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<GradeVO> pageByStudent(Long studentId, Long enrollmentId, Long courseId, int page, int size) {
        LambdaQueryWrapper<Grade> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Grade::getUserId, studentId);
        if (enrollmentId != null) {
            // enrollmentId not directly in grades table — use courseId as proxy when provided
        }
        if (courseId != null) {
            wrapper.eq(Grade::getCourseId, courseId);
        }
        wrapper.isNull(Grade::getDeletedAt).orderByDesc(Grade::getCreatedAt);

        IPage<Grade> gradePage = gradeRepository.selectPage(new Page<>(page + 1, size), wrapper);

        List<GradeVO> vos = batchConvertToVO(gradePage.getRecords());
        return PageResult.of(vos, gradePage.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public GradeVO getById(Long id) {
        Grade grade = gradeRepository.selectById(id);
        if (grade == null) {
            throw new BusinessException(ErrorCode.GRADE_NOT_FOUND);
        }
        // SECURITY: 只有课程教师、学生本人、ADMIN 或 ACADEMIC 可查看成绩
        if (grade.getCourseId() != null) {
            Course course = courseRepository.selectById(grade.getCourseId());
            if (course != null && !SecurityUtil.isAdminOrAcademic()
                    && !SecurityUtil.getCurrentUserId().equals(course.getTeacherId())
                    && !SecurityUtil.getCurrentUserId().equals(grade.getUserId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        }
        return convertToVO(grade);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeVO create(GradeCreateRequest request, Long teacherId) {
        // SECURITY: 校验当前用户是否为该课程的授课教师
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权为该课程创建成绩");
        }

        // P1: 重复提交防护 — 同一课程+学生+练习只允许一条成绩
        LambdaQueryWrapper<Grade> dupWrapper = new LambdaQueryWrapper<>();
        dupWrapper.eq(Grade::getCourseId, request.getCourseId())
                  .eq(Grade::getUserId, request.getUserId())
                  .isNull(Grade::getDeletedAt);
        if (request.getExerciseId() != null) {
            dupWrapper.eq(Grade::getExerciseId, request.getExerciseId());
        } else {
            dupWrapper.isNull(Grade::getExerciseId);
        }
        if (gradeRepository.selectCount(dupWrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该学生此课程已有成绩记录，请勿重复提交");
        }

        Grade grade = new Grade();
        grade.setCourseId(request.getCourseId());
        grade.setUserId(request.getUserId());
        grade.setExerciseId(request.getExerciseId());
        grade.setScore(request.getScore());
        grade.setTotalScore(request.getTotalScore());
        grade.setPassed(request.getPassed());
        grade.setAttemptNo(request.getAttemptNo());
        grade.setDuration(request.getDuration());
        grade.setComment(sanitizeComment(request.getComment()));
        grade.setGradedBy(teacherId);
        grade.setGradedAt(LocalDateTime.now());
        grade.setCreatedAt(LocalDateTime.now());
        grade.setUpdatedAt(LocalDateTime.now());

        // R8 P1-C-1: 防并发重复插入（TOCTOU）
        try {
            gradeRepository.insert(grade);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            Grade existing = gradeRepository.selectOne(dupWrapper);
            if (existing != null) {
                return convertToVO(existing);
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "成绩记录已存在，请刷新后重试");
        }
        return convertToVO(grade);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeVO update(Long id, GradeUpdateRequest request, Long teacherId) {
        Grade grade = gradeRepository.selectById(id);
        if (grade == null) {
            throw new BusinessException(ErrorCode.GRADE_NOT_FOUND);
        }

        // Phase E: 捕获修改前的原成绩值用于审计
        BigDecimal oldScore = grade.getScore();

        // EXAM-NEW-4 修复:教师越权校验 — 只有课程教师或 ADMIN 可修改成绩
        if (grade.getCourseId() != null) {
            Course course = courseRepository.selectById(grade.getCourseId());
            if (course != null && !SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        }

        // P1C-089: 成绩锁定 — COMPLETED 状态后禁止修改成绩
        if (grade.getCourseId() != null && grade.getUserId() != null) {
            Enrollment enrollment = enrollmentRepository.selectOne(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getCourseId, grade.getCourseId())
                            .eq(Enrollment::getUserId, grade.getUserId()));
            if (enrollment != null && EnrollmentStatus.COMPLETED.getValue().equals(enrollment.getEnrollmentStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程已完成，成绩已锁定，无法修改");
            }
        }

        if (request.getScore() != null) {
            // MISC-NEW-3 修复:校验 score <= totalScore
            BigDecimal ts = request.getTotalScore() != null ? request.getTotalScore() : grade.getTotalScore();
            if (ts != null && request.getScore().compareTo(ts) > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "得分不能超过总分");
            }
            grade.setScore(request.getScore());
        }
        if (request.getTotalScore() != null) {
            grade.setTotalScore(request.getTotalScore());
        }
        if (request.getPassed() != null) {
            grade.setPassed(request.getPassed());
        }
        if (request.getDuration() != null) {
            grade.setDuration(request.getDuration());
        }
        if (request.getComment() != null) {
            grade.setComment(sanitizeComment(request.getComment()));
        }
        grade.setGradedBy(teacherId);
        grade.setGradedAt(LocalDateTime.now());
        grade.setUpdatedAt(LocalDateTime.now());

        // P0: 乐观锁影响行=0 → 抛 CONCURRENT_MODIFICATION
        if (gradeRepository.updateById(grade) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "成绩已被其他操作修改，请刷新后重试");
        }

        // Phase E: 成绩变更审计追踪
        try {
            Long enrollmentId = findEnrollmentId(grade.getCourseId(), grade.getUserId());
            if (enrollmentId != null) {
                scoreHistoryService.recordChange(enrollmentId, "score",
                    oldScore != null ? oldScore.toString() : null,
                    request.getScore() != null ? request.getScore().toString() : null,
                    "UPDATE", "修改成绩", teacherId);
            }
        } catch (Exception e) {
            log.warn("[Grade] 记录成绩审计失败, 不影响主流程", e);
        }

        return convertToVO(grade);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Grade grade = gradeRepository.selectById(id);
        if (grade == null) {
            throw new BusinessException(ErrorCode.GRADE_NOT_FOUND);
        }
        // P0-8: 删除权限校验 — 只有课程教师或 ADMIN 可删除
        if (grade.getCourseId() != null) {
            Course course = courseRepository.selectById(grade.getCourseId());
            if (course != null && !SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权删除该成绩记录");
            }
        }
        gradeRepository.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeVO teacherGrade(GradeTeacherSubmitRequest request, Long teacherId) {
        // 1. 通过 enrollmentId 反查 courseId 和 studentId
        Enrollment enrollment = enrollmentRepository.selectById(request.getEnrollmentId());
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND, "选课记录不存在");
        }
        Long courseId = enrollment.getCourseId();
        Long studentId = enrollment.getUserId();

        // P1C-089: 成绩锁定 — COMPLETED 状态后禁止修改成绩
        if (EnrollmentStatus.COMPLETED.getValue().equals(enrollment.getEnrollmentStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程已完成，成绩已锁定，无法修改");
        }

        // 2. 校验教师拥有该课程
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权批改该课程成绩");
        }

        // 3. 查找是否已有成绩记录（同课程+同学生，无 exerciseId）
        LambdaQueryWrapper<Grade> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(Grade::getCourseId, courseId)
                    .eq(Grade::getUserId, studentId)
                    .isNull(Grade::getExerciseId)
                    .isNull(Grade::getDeletedAt);
        Grade grade = gradeRepository.selectOne(existWrapper);

        String safeComment = sanitizeComment(request.getComment());

        // Phase E: 捕获修改前的原成绩值用于审计
        String oldScoreStr = grade != null && grade.getScore() != null ? grade.getScore().toString() : null;

        if (grade != null) {
            // 更新已有记录
            grade.setScore(request.getScore());
            grade.setComment(safeComment);
            grade.setGradedBy(teacherId);
            grade.setGradedAt(LocalDateTime.now());
            grade.setUpdatedAt(LocalDateTime.now());
            // P0: 乐观锁影响行=0 → 抛 CONCURRENT_MODIFICATION
            if (gradeRepository.updateById(grade) == 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "成绩已被其他操作修改，请刷新后重试");
            }
        } else {
            // 新建记录
            // CON-004 修复: 使用 saveOrUpdate 原子操作替代 selectOne + insert, 防止并发重复插入
            grade = new Grade();
            grade.setCourseId(courseId);
            grade.setUserId(studentId);
            grade.setScore(request.getScore());
            grade.setComment(safeComment);
            grade.setGradedBy(teacherId);
            grade.setGradedAt(LocalDateTime.now());
            grade.setCreatedAt(LocalDateTime.now());
            grade.setUpdatedAt(LocalDateTime.now());
            try {
                gradeRepository.insert(grade);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发插入冲突: 重新查询已存在的记录并返回
                log.info("[teacherGrade] 并发插入冲突, 降级查询: courseId={}, userId={}", courseId, studentId);
                grade = gradeRepository.selectOne(existWrapper);
                if (grade == null) {
                    throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "成绩记录冲突后查询失败");
                }
            }
        }

        // R8 P0-5: 成绩发布后通知学生
        String courseTitle = course != null ? course.getTitle() : "课程";
        try {
            notificationService.notifyAsync(studentId, NotificationType.GRADE_ISSUED,
                    "成绩已发布",
                    "您在《" + courseTitle + "》中的成绩已由教师录入，请查看。",
                    courseId);
        } catch (Exception e) {
            log.warn("[Grade] 通知学生成绩发布失败 userId={} courseId={}", studentId, courseId, e);
        }

        // Phase E: 成绩变更审计追踪
        try {
            scoreHistoryService.recordChange(request.getEnrollmentId(), "score",
                oldScoreStr,
                request.getScore().toString(),
                "TEACHER_GRADE", "教师评分", teacherId);
        } catch (Exception e) {
            log.warn("[Grade] 记录成绩审计失败, 不影响主流程", e);
        }

        GradeVO vo = batchConvertToVO(Collections.singletonList(grade)).get(0);
        vo.setEnrollmentId(request.getEnrollmentId());
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ExerciseRecordVO> getPendingReview(int page, int size, Long currentUserId) {
        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseRecord::getNeedsManualGrading, true)
               .isNull(ExerciseRecord::getDeletedAt);

        // TEACHER 数据隔离 — 仅返回自己授课课程下练习的记录；ADMIN 不限制
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            LambdaQueryWrapper<Course> courseWrapper = new LambdaQueryWrapper<>();
            courseWrapper.eq(Course::getTeacherId, currentUserId).isNull(Course::getDeletedAt);
            List<Long> teacherCourseIds = courseRepository.selectList(courseWrapper).stream()
                    .map(Course::getId).collect(Collectors.toList());
            if (teacherCourseIds.isEmpty()) {
                return PageResult.of(new ArrayList<>(), 0L, page, size);
            }
            LambdaQueryWrapper<Exercise> exerciseWrapper = new LambdaQueryWrapper<>();
            exerciseWrapper.in(Exercise::getCourseId, teacherCourseIds).isNull(Exercise::getDeletedAt);
            List<Long> exerciseIds = exerciseRepository.selectList(exerciseWrapper).stream()
                    .map(Exercise::getId).collect(Collectors.toList());
            if (exerciseIds.isEmpty()) {
                return PageResult.of(new ArrayList<>(), 0L, page, size);
            }
            wrapper.in(ExerciseRecord::getExerciseId, exerciseIds);
        }

        wrapper.orderByDesc(ExerciseRecord::getSubmittedAt);
        IPage<ExerciseRecord> recordPage = exerciseRecordRepository.selectPage(new Page<>(page + 1, size), wrapper);
        List<ExerciseRecordVO> vos = toRecordVOList(recordPage.getRecords());
        return PageResult.of(vos, recordPage.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualGrade(Long recordId, Long questionId, Double score, String comment, Long teacherId) {
        // Phase 14: manualGrade 已提取到 ManualGradingServiceImpl
        // GradeServiceImpl.manualGrade 仅做参数校验与委托，
        // 完整业务实现（含答案 JSON 解析、score 合并、grade 同步、审计与通知）
        // 全部在 ManualGradingServiceImpl.manualGrade() 中
        if (recordId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "recordId 不能为空");
        }
        if (questionId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "questionId 不能为空");
        }
        if (score == null || score < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "score 非法");
        }
        manualGradingService.manualGrade(recordId, questionId, score, comment, teacherId);
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
     * ExerciseRecord -> VO 批量转换（批量预加载练习标题，避免 N+1）
     */
    private List<ExerciseRecordVO> toRecordVOList(List<ExerciseRecord> records) {
        if (records.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> exerciseIds = records.stream()
                .map(ExerciseRecord::getExerciseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Exercise> exerciseMap = exerciseIds.isEmpty() ? Collections.emptyMap()
                : exerciseRepository.selectBatchIds(exerciseIds).stream()
                        .collect(Collectors.toMap(Exercise::getId, e -> e));

        return records.stream().map(r -> {
            ExerciseRecordVO vo = new ExerciseRecordVO();
            vo.setId(r.getId());
            vo.setExerciseId(r.getExerciseId());
            Exercise ex = exerciseMap.get(r.getExerciseId());
            vo.setExerciseTitle(ex != null ? ex.getTitle() : null);
            vo.setUserId(r.getUserId());
            vo.setAttemptNo(r.getAttemptNo());
            vo.setScore(r.getScore());
            vo.setTotalScore(r.getTotalScore());
            vo.setPassed(r.getPassed());
            vo.setDuration(r.getDuration());
            vo.setAnswers(r.getAnswers());
            vo.setSubmittedAt(r.getSubmittedAt());
            return vo;
        }).collect(Collectors.toList());
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

    /**
     * 批量转换 — 预加载关联实体，避免 N+1
     */
    private List<GradeVO> batchConvertToVO(List<Grade> grades) {
        if (grades.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集所有需要查询的 ID
        Set<Long> courseIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> exerciseIds = new HashSet<>();
        for (Grade g : grades) {
            if (g.getCourseId() != null) courseIds.add(g.getCourseId());
            if (g.getUserId() != null) userIds.add(g.getUserId());
            if (g.getGradedBy() != null) userIds.add(g.getGradedBy());
            if (g.getExerciseId() != null) exerciseIds.add(g.getExerciseId());
        }

        // 批量查询
        Map<Long, Course> courseMap = courseIds.isEmpty() ? Collections.emptyMap()
                : courseRepository.selectBatchIds(courseIds).stream()
                        .collect(Collectors.toMap(Course::getId, c -> c));
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userRepository.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Exercise> exerciseMap = exerciseIds.isEmpty() ? Collections.emptyMap()
                : exerciseRepository.selectBatchIds(exerciseIds).stream()
                        .collect(Collectors.toMap(Exercise::getId, e -> e));

        // Phase 6 P0: 批量查询选课（courseId, userId）→ enrollmentId
        Map<Long, Map<Long, Long>> enrollmentIdMap = new HashMap<>();
        if (!courseIds.isEmpty() && !userIds.isEmpty()) {
            LambdaQueryWrapper<Enrollment> ew = new LambdaQueryWrapper<>();
            ew.in(Enrollment::getCourseId, courseIds)
              .in(Enrollment::getUserId, userIds)
              .isNull(Enrollment::getDeletedAt);
            for (Enrollment e : enrollmentRepository.selectList(ew)) {
                enrollmentIdMap.computeIfAbsent(e.getCourseId(), k -> new HashMap<>())
                               .put(e.getUserId(), e.getId());
            }
        }

        // P1-C-Grade-N+1: 批量预加载 ExerciseRecord，避免 per-grade selectOne N+1
        // 收集有 exerciseId 的 grades 的 (userId, exerciseId, attemptNo) 组合
        List<ExerciseRecordKey> recordKeys = new ArrayList<>();
        for (Grade g : grades) {
            if (g.getExerciseId() != null && g.getUserId() != null) {
                recordKeys.add(new ExerciseRecordKey(
                        g.getUserId(),
                        g.getExerciseId(),
                        g.getAttemptNo() != null ? g.getAttemptNo() : 0));
            }
        }
        // 批量查询所有相关 ExerciseRecord（按 id 倒序，取每个组合最近一条）
        Map<ExerciseRecordKey, ExerciseRecord> recordMap = new HashMap<>();
        if (!recordKeys.isEmpty()) {
            Set<Long> recExerciseIds = recordKeys.stream().map(k -> k.exerciseId).collect(Collectors.toSet());
            Set<Long> recUserIds = recordKeys.stream().map(k -> k.userId).collect(Collectors.toSet());
            LambdaQueryWrapper<ExerciseRecord> recWrapper = new LambdaQueryWrapper<>();
            recWrapper.in(ExerciseRecord::getExerciseId, recExerciseIds)
                     .in(ExerciseRecord::getUserId, recUserIds)
                     .isNull(ExerciseRecord::getDeletedAt)
                     .orderByDesc(ExerciseRecord::getId);
            List<ExerciseRecord> allRecords = exerciseRecordRepository.selectList(recWrapper);
            for (ExerciseRecord r : allRecords) {
                ExerciseRecordKey key = new ExerciseRecordKey(
                        r.getUserId(), r.getExerciseId(),
                        r.getAttemptNo() != null ? r.getAttemptNo() : 0);
                // id 倒序，第一个遇到即最近
                recordMap.putIfAbsent(key, r);
            }
        }

        return grades.stream().map(grade -> {
            GradeVO vo = new GradeVO();
            vo.setId(grade.getId());
            vo.setCourseId(grade.getCourseId());
            vo.setUserId(grade.getUserId());
            vo.setExerciseId(grade.getExerciseId());
            vo.setScore(grade.getScore());
            vo.setTotalScore(grade.getTotalScore());
            vo.setPassed(grade.getPassed());
            vo.setAttemptNo(grade.getAttemptNo());
            vo.setDuration(grade.getDuration());
            vo.setSubmittedAt(grade.getSubmittedAt());
            vo.setGradedBy(grade.getGradedBy());
            vo.setGradedAt(grade.getGradedAt());
            vo.setCreatedAt(grade.getCreatedAt());
            vo.setComment(grade.getComment());

            // P1-C 修复 (2026-08-04): 关联练习作答记录，识别"待人工批改"的主观题，
            // 供前端成绩页展示批改入口与逐题批改（此前主观题永远 0 分且无批改入口）。
            // P1-C-Grade-N+1 优化: 批量预加载替代 per-grade selectOne，从 recordMap 查找。
            try {
                ExerciseRecord record = null;
                if (grade.getExerciseId() != null && grade.getUserId() != null) {
                    record = recordMap.get(new ExerciseRecordKey(
                            grade.getUserId(),
                            grade.getExerciseId(),
                            grade.getAttemptNo() != null ? grade.getAttemptNo() : 0));
                }
                if (record != null) {
                    vo.setRecordId(record.getId());
                    boolean needsManual = Boolean.TRUE.equals(record.getNeedsManualGrading());
                    vo.setNeedsManualGrading(needsManual);
                    if (needsManual && record.getAnswers() != null && !record.getAnswers().isBlank()) {
                        List<Map<String, Object>> answerList = objectMapper.readValue(
                                record.getAnswers(),
                                new TypeReference<List<Map<String, Object>>>() {});
                        List<Map<String, Object>> pending = new ArrayList<>();
                        for (Map<String, Object> answer : answerList) {
                            if (Boolean.TRUE.equals(answer.get("needsManualGrading"))) {
                                Map<String, Object> item = new HashMap<>();
                                Object qidObj = answer.get("questionId");
                                item.put("questionId", qidObj instanceof Number
                                        ? ((Number) qidObj).longValue() : null);
                                item.put("studentAnswer", answer.get("answer"));
                                // 满分取练习总分（单题主观题即该题满分；answer.score 是学生得分 0）
                                item.put("maxScore", grade.getTotalScore() != null
                                        ? grade.getTotalScore().intValue() : 0);
                                pending.add(item);
                            }
                        }
                        vo.setPendingQuestions(pending);
                    }
                }
            } catch (Exception e) {
                log.warn("[Grade] 关联作答记录解析失败 gradeId={}", grade.getId(), e);
            }

            Course course = courseMap.get(grade.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
            }
            User student = userMap.get(grade.getUserId());
            if (student != null) {
                vo.setStudentName(student.getRealName() != null ? student.getRealName() : student.getUsername());
            }
            Exercise exercise = exerciseMap.get(grade.getExerciseId());
            if (exercise != null) {
                vo.setExerciseTitle(exercise.getTitle());
            }
            User grader = userMap.get(grade.getGradedBy());
            if (grader != null) {
                vo.setGradedByName(grader.getRealName() != null ? grader.getRealName() : grader.getUsername());
            }
            // Phase 6 P0: 填充 enrollmentId（从批量预加载的选课映射）
            if (grade.getCourseId() != null && grade.getUserId() != null) {
                Map<Long, Long> byCourse = enrollmentIdMap.get(grade.getCourseId());
                if (byCourse != null) {
                    Long eid = byCourse.get(grade.getUserId());
                    if (eid != null) {
                        vo.setEnrollmentId(eid);
                    }
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * P1/P0-6 修复: 单条转换委托 batchConvertToVO，消除 N+1
     */
    private GradeVO convertToVO(Grade grade) {
        List<GradeVO> vos = batchConvertToVO(Collections.singletonList(grade));
        return vos.isEmpty() ? new GradeVO() : vos.get(0);
    }

    /**
     * Phase E: 根据 courseId + userId 查询选课记录 ID
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

    /**
     * P1 安全修复: 评语 XSS 过滤 — 使用 Jsoup Safelist 替代可被绕过的正则 {@code <[^>]*>}。
     */
    private String sanitizeComment(String comment) {
        return com.microcourse.util.XssSanitizer.sanitize(comment);
    }

    // P2-014: 导出上限改为可配置常量，后续可迁移到 application.yml
    private static final int EXPORT_MAX_SIZE = 10000;

    @Override
    public java.util.List<GradeVO> getGradesForExport(Long courseId, Long currentUserId) {
        // 用 page 方法取最大 EXPORT_MAX_SIZE 条
        PageResult<GradeVO> result = page(courseId, null, 0, EXPORT_MAX_SIZE);
        return result.getItems();
    }

    /**
     * P1-C-Grade-N+1: ExerciseRecord 复合键，用于批量查询结果去重
     */
    private static class ExerciseRecordKey {
        final Long userId;
        final Long exerciseId;
        final int attemptNo;

        ExerciseRecordKey(Long userId, Long exerciseId, int attemptNo) {
            this.userId = userId;
            this.exerciseId = exerciseId;
            this.attemptNo = attemptNo;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExerciseRecordKey that = (ExerciseRecordKey) o;
            return attemptNo == that.attemptNo
                    && Objects.equals(userId, that.userId)
                    && Objects.equals(exerciseId, that.exerciseId);
        }

        @Override public int hashCode() {
            return Objects.hash(userId, exerciseId, attemptNo);
        }
    }
}
