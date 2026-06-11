package com.microcourse.service;

import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.PageResult;

public interface CourseService {

    PageResult<CourseVO> page(CoursePageQuery query);

    CourseVO getById(Long id);

    CourseVO create(CourseCreateRequest request);

    CourseVO update(Long id, CourseUpdateRequest request);

    void updateStatus(Long id, Integer status);

    void delete(Long id);
}