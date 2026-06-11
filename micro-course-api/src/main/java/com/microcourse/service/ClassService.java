package com.microcourse.service;

import com.microcourse.dto.ClassCreateRequest;
import com.microcourse.dto.ClassUpdateRequest;
import com.microcourse.dto.ClassVO;
import com.microcourse.dto.PageResult;

public interface ClassService {

    PageResult<ClassVO> page(int page, int size);

    ClassVO getById(Long id);

    ClassVO create(ClassCreateRequest request);

    ClassVO update(Long id, ClassUpdateRequest request);

    void delete(Long id);
}
