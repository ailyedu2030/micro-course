package com.microcourse.service;

import com.microcourse.dto.MajorCreateRequest;
import com.microcourse.dto.MajorUpdateRequest;
import com.microcourse.dto.MajorVO;
import com.microcourse.dto.PageResult;

public interface MajorService {

    PageResult<MajorVO> page(int page, int size);

    MajorVO getById(Long id);

    MajorVO create(MajorCreateRequest request);

    MajorVO update(Long id, MajorUpdateRequest request);

    void delete(Long id);
}