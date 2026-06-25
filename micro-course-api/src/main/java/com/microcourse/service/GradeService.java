package com.microcourse.service;

import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.GradeCreateRequest;
import com.microcourse.dto.GradeTeacherSubmitRequest;
import com.microcourse.dto.GradeUpdateRequest;
import com.microcourse.dto.GradeVO;
import com.microcourse.dto.PageResult;

import java.util.Map;

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

    /**
     * 获取待批改的练习记录列表（含主观题 needsManualGrading=true 的记录）
     * TEACHER 仅返回自己授课课程的记录，ADMIN 返回全部
     */
    PageResult<ExerciseRecordVO> getPendingReview(int page, int size, Long currentUserId);

    /**
     * 教师手动批改主观题：将单题 score/comment 写回 answers JSON，并同步更新记录与 grades 表
     * body: { "questionId": Long, "score": Integer, "comment": String }
     */
    void manualGrade(Long recordId, Map<String, Object> body, Long teacherId);
}