package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.*;
import com.microcourse.entity.*;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.*;
import com.microcourse.service.GradeService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GradeServiceImpl implements GradeService {

    private final GradeRepository gradeRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ObjectMapper objectMapper;

    public GradeServiceImpl(
            GradeRepository gradeRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            ExerciseRepository exerciseRepository,
            EnrollmentRepository enrollmentRepository,
            ExerciseRecordRepository exerciseRecordRepository,
            ObjectMapper objectMapper) {
        this.gradeRepository = gradeRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.objectMapper = objectMapper;
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
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "成绩记录不存在");
        }
        // SECURITY: 只有课程教师、学生本人或 ADMIN 可查看成绩
        if (grade.getCourseId() != null) {
            Course course = courseRepository.selectById(grade.getCourseId());
            if (course != null && !SecurityUtil.isOwnerOrAdmin(course.getTeacherId())
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

        gradeRepository.insert(grade);
        return convertToVO(grade);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeVO update(Long id, GradeUpdateRequest request, Long teacherId) {
        Grade grade = gradeRepository.selectById(id);
        if (grade == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "成绩记录不存在");
        }

        // EXAM-NEW-4 修复:教师越权校验 — 只有课程教师或 ADMIN 可修改成绩
        if (grade.getCourseId() != null) {
            Course course = courseRepository.selectById(grade.getCourseId());
            if (course != null && !SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
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

        gradeRepository.updateById(grade);
        return convertToVO(grade);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Grade grade = gradeRepository.selectById(id);
        if (grade == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "成绩记录不存在");
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

        if (grade != null) {
            // 更新已有记录
            grade.setScore(request.getScore());
            grade.setComment(safeComment);
            grade.setGradedBy(teacherId);
            grade.setGradedAt(LocalDateTime.now());
            grade.setUpdatedAt(LocalDateTime.now());
            gradeRepository.updateById(grade);
        } else {
            // 新建记录
            grade = new Grade();
            grade.setCourseId(courseId);
            grade.setUserId(studentId);
            grade.setScore(request.getScore());
            grade.setComment(safeComment);
            grade.setGradedBy(teacherId);
            grade.setGradedAt(LocalDateTime.now());
            grade.setCreatedAt(LocalDateTime.now());
            grade.setUpdatedAt(LocalDateTime.now());
            gradeRepository.insert(grade);
        }

        GradeVO vo = batchConvertToVO(Collections.singletonList(grade)).get(0);
        vo.setEnrollmentId(request.getEnrollmentId());
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ExerciseRecordVO> getPendingReview(int page, int size, Long currentUserId) {
        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(ExerciseRecord::getDeletedAt)
               // answers JSON 中存在尚未人工批改的主观题
               .like(ExerciseRecord::getAnswers, "\"needsManualGrading\":true");

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
    public void manualGrade(Long recordId, Map<String, Object> body, Long teacherId) {
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

        Long questionId = toLong(body.get("questionId"));
        Integer newScore = toInteger(body.get("score"));
        String comment = sanitizeComment(toStringVal(body.get("comment")));
        if (questionId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "questionId 不能为空");
        }
        if (newScore == null || newScore < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "score 非法");
        }

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
        if (fullScore != null && newScore > fullScore) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "得分不能超过该题满分");
        }

        // 写回单题 score / comment / 标记已批改
        target.put("score", newScore);
        target.put("comment", comment);
        target.put("isCorrect", newScore > 0);
        target.put("needsManualGrading", false);

        // 重算记录总得分
        int total = 0;
        for (Map<String, Object> item : items) {
            Integer s = toInteger(item.get("score"));
            if (s != null) {
                total += s;
            }
        }

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
        exerciseRecordRepository.updateById(record);

        // 同步更新 grades 表对应记录（优先按 attemptNo 精确匹配本次作答）
        Grade grade = findGradeForRecord(record, exercise.getCourseId());
        if (grade != null) {
            grade.setScore(BigDecimal.valueOf(total));
            grade.setPassed(passed);
            grade.setComment(comment);
            grade.setGradedBy(teacherId);
            grade.setGradedAt(LocalDateTime.now());
            grade.setUpdatedAt(LocalDateTime.now());
            gradeRepository.updateById(grade);
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
            gradeRepository.insert(ng);
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

    private String toStringVal(Object o) {
        return o == null ? null : o.toString();
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
     * P1 安全修复: 评语 XSS 过滤 — 使用 Jsoup Safelist 替代可被绕过的正则 {@code <[^>]*>}。
     */
    private String sanitizeComment(String comment) {
        return com.microcourse.util.XssSanitizer.sanitize(comment);
    }
}