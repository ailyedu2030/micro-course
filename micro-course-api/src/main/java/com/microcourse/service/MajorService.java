package com.microcourse.service;

import com.microcourse.dto.MajorCreateRequest;
import com.microcourse.dto.MajorUpdateRequest;
import com.microcourse.dto.MajorVO;
import com.microcourse.dto.PageResult;

public interface MajorService {

    PageResult<MajorVO> page(int page, int size);

    MajorVO getById(Long id);

    /**
     * 【P1-C 修复】按院系 ID 查询专业列表
     * @param departmentId 院系 ID
     * @return List<MajorVO>
     */
    java.util.List<MajorVO> listByDepartmentId(Long departmentId);

    MajorVO create(MajorCreateRequest request);

    MajorVO update(Long id, MajorUpdateRequest request);

    void delete(Long id);
}