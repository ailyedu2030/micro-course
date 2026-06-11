package com.microcourse.service;

import com.microcourse.dto.CourseCategoryCreateRequest;
import com.microcourse.dto.CourseCategoryUpdateRequest;
import com.microcourse.dto.CourseCategoryVO;
import com.microcourse.dto.PageResult;

public interface CourseCategoryService {

    PageResult<CourseCategoryVO> page(int page, int size);

    CourseCategoryVO getById(Long id);

    CourseCategoryVO create(CourseCategoryCreateRequest request);

    CourseCategoryVO update(Long id, CourseCategoryUpdateRequest request);

    void delete(Long id);
}