package com.microcourse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microcourse.dto.DepartmentCreateRequest;
import com.microcourse.dto.DepartmentUpdateRequest;
import com.microcourse.dto.DepartmentVO;
import com.microcourse.dto.PageResult;

public interface DepartmentService {

    PageResult<DepartmentVO> page(int page, int size);

    DepartmentVO getById(Long id);

    DepartmentVO create(DepartmentCreateRequest request);

    DepartmentVO update(Long id, DepartmentUpdateRequest request);

    void delete(Long id);
}