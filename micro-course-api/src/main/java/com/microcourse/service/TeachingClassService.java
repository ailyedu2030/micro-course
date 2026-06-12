package com.microcourse.service;

import com.microcourse.dto.*;

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
}