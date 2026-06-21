package com.microcourse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microcourse.dto.DepartmentCreateRequest;
import com.microcourse.dto.DepartmentStatsVO;
import com.microcourse.dto.DepartmentUpdateRequest;
import com.microcourse.dto.DepartmentVO;
import com.microcourse.dto.PageResult;

public interface DepartmentService {

    PageResult<DepartmentVO> page(int page, int size);

    DepartmentVO getById(Long id);

    DepartmentVO create(DepartmentCreateRequest request);

    DepartmentVO update(Long id, DepartmentUpdateRequest request);

    void delete(Long id);

    /**
     * Round 5-3 (P1-10): 计算院系统计数据（开课数 / 学生数 / 选课数）。
     *
     * @param departmentId 院系 ID
     * @return 院系统计 VO；院系不存在抛 DEPARTMENT_NOT_FOUND
     */
    DepartmentStatsVO computeStats(Long departmentId);
}