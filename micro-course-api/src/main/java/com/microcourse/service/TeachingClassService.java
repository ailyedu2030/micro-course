package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.TeachingClassCreateRequest;
import com.microcourse.dto.TeachingClassStudentVO;
import com.microcourse.dto.TeachingClassUpdateRequest;
import com.microcourse.dto.TeachingClassVO;

import java.util.List;

public interface TeachingClassService {

    PageResult<TeachingClassVO> page(int page, int size, Long teacherId, Long courseId, String semester, Integer status);

    TeachingClassVO getById(Long id);

    TeachingClassVO create(TeachingClassCreateRequest req);

    TeachingClassVO update(Long id, TeachingClassUpdateRequest req);

    void delete(Long id);

    List<TeachingClassStudentVO> getClassStudents(Long classId);

    void addStudent(Long classId, Long userId);

    void removeStudent(Long classId, Long userId);

    void updateStudentStatus(Long classId, Long userId, String status);

    /**
     * 结课（ACTIVE → COMPLETED）。状态机校验 + 乐观锁更新。
     *
     * @param classId    教学班 ID
     * @param operatorId 操作人 ID（用于审计日志）
     */
    void complete(Long classId, Long operatorId);

    /**
     * 停开（ACTIVE → CANCELLED）。状态机校验 + 乐观锁更新；停开原因必填。
     *
     * @param classId    教学班 ID
     * @param reason     停开原因（不可为空）
     * @param operatorId 操作人 ID（用于审计日志）
     */
    void cancel(Long classId, String reason, Long operatorId);
}