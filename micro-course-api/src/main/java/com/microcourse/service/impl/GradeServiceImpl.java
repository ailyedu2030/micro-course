package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    public GradeServiceImpl(
            GradeRepository gradeRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            ExerciseRepository exerciseRepository,
            EnrollmentRepository enrollmentRepository) {
        this.gradeRepository = gradeRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<GradeVO> page(Long courseId, Long studentId, int page, int size) {
        LambdaQueryWrapper<Grade> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Grade::getCourseId, courseId);
        }
        if (studentId != null) {
            wrapper.eq(Grade::getStudentId, studentId);
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
        wrapper.eq(Grade::getStudentId, studentId);
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
        return convertToVO(grade);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeVO create(GradeCreateRequest request, Long teacherId) {
        // P1: 重复提交防护 — 同一课程+学生+练习只允许一条成绩
        LambdaQueryWrapper<Grade> dupWrapper = new LambdaQueryWrapper<>();
        dupWrapper.eq(Grade::getCourseId, request.getCourseId())
                  .eq(Grade::getStudentId, request.getStudentId())
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
        grade.setStudentId(request.getStudentId());
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
                    .eq(Grade::getStudentId, studentId)
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
            grade.setStudentId(studentId);
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
            if (g.getStudentId() != null) userIds.add(g.getStudentId());
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
            vo.setStudentId(grade.getStudentId());
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
            User student = userMap.get(grade.getStudentId());
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
     * P2: 评语 XSS 过滤 — 剥离 HTML 标签
     */
    private String sanitizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        return comment.replaceAll("<[^>]*>", "").trim();
    }
}