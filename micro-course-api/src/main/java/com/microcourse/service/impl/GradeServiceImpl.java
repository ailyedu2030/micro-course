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

    public GradeServiceImpl(
            GradeRepository gradeRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            ExerciseRepository exerciseRepository) {
        this.gradeRepository = gradeRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
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
        Grade grade = new Grade();
        grade.setCourseId(request.getCourseId());
        grade.setStudentId(request.getStudentId());
        grade.setExerciseId(request.getExerciseId());
        grade.setScore(request.getScore());
        grade.setTotalScore(request.getTotalScore());
        grade.setPassed(request.getPassed());
        grade.setAttemptNo(request.getAttemptNo());
        grade.setDuration(request.getDuration());
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

        if (request.getScore() != null) {
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
        gradeRepository.deleteById(id);
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

    private GradeVO convertToVO(Grade grade) {
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

        // 填充课程名
        if (grade.getCourseId() != null) {
            Course course = courseRepository.selectById(grade.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
            }
        }

        // 填充学生名
        if (grade.getStudentId() != null) {
            User student = userRepository.selectById(grade.getStudentId());
            if (student != null) {
                vo.setStudentName(student.getRealName() != null ? student.getRealName() : student.getUsername());
            }
        }

        // 填充练习标题
        if (grade.getExerciseId() != null) {
            Exercise exercise = exerciseRepository.selectById(grade.getExerciseId());
            if (exercise != null) {
                vo.setExerciseTitle(exercise.getTitle());
            }
        }

        // 填充批改人名称
        if (grade.getGradedBy() != null) {
            User grader = userRepository.selectById(grade.getGradedBy());
            if (grader != null) {
                vo.setGradedByName(grader.getRealName() != null ? grader.getRealName() : grader.getUsername());
            }
        }

        return vo;
    }
}