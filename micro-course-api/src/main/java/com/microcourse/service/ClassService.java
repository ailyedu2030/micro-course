package com.microcourse.service;

import com.microcourse.dto.ClassCreateRequest;
import com.microcourse.dto.ClassStudentVO;
import com.microcourse.dto.ClassUpdateRequest;
import com.microcourse.dto.ClassVO;
import com.microcourse.dto.PageResult;

import java.util.List;

public interface ClassService {

    PageResult<ClassVO> page(int page, int size);

    ClassVO getById(Long id);

    ClassVO create(ClassCreateRequest request);

    ClassVO update(Long id, ClassUpdateRequest request);

    void delete(Long id);

    /**
     * Round 5-3 (P1-10): 获取班级学生名单（users 表按 classId 关联）。
     *
     * @param classId 班级 ID
     * @return 学生 VO 列表；班级不存在抛 CLASS_NOT_FOUND
     */
    List<ClassStudentVO> getStudents(Long classId);

    /**
     * 【P1-C 修复】按专业 ID 查询班级列表
     * @param majorId 专业 ID
     * @return 班级列表
     */
    List<ClassVO> listByMajorId(Long majorId);
}
