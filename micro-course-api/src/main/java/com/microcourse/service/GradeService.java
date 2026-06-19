package com.microcourse.service;

import com.microcourse.dto.*;

public interface GradeService {

    PageResult<GradeVO> page(Long courseId, Long studentId, int page, int size);

    PageResult<GradeVO> pageByStudent(Long studentId, Long enrollmentId, Long courseId, int page, int size);

    GradeVO getById(Long id);

    GradeVO create(GradeCreateRequest request, Long teacherId);

    GradeVO update(Long id, GradeUpdateRequest request, Long teacherId);

    void delete(Long id);

    /**
     * 教师通过 enrollmentId 批改成绩（前端直接提交 enrollmentId + score + comment）
     */
    GradeVO teacherGrade(GradeTeacherSubmitRequest request, Long teacherId);
}