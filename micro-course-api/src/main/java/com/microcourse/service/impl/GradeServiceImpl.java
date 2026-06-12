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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    public PageResult<GradeVO> page(Long courseId, Long studentId, int page, int size) {
        LambdaQueryWrapper<Grade> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Grade::getCourseId, courseId);
        }
        if (studentId != null) {
            wrapper.eq(Grade::getStudentId, studentId);
        }
        wrapper.isNull(Grade::getDeletedAt).orderByDesc(Grade::getCreatedAt);

        IPage<Grade> gradePage = gradeRepository.selectPage(new Page<>(page, size), wrapper);

        List<GradeVO> vos = gradePage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
        return PageResult.of(vos, gradePage.getTotal(), page, size);
    }

    @Override
    public GradeVO getById(Long id) {
        Grade grade = gradeRepository.selectById(id);
        if (grade == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "成绩记录不存在");
        }
        return convertToVO(grade);
    }

    @Override
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
    public void delete(Long id) {
        Grade grade = gradeRepository.selectById(id);
        if (grade == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "成绩记录不存在");
        }
        gradeRepository.deleteById(id);
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