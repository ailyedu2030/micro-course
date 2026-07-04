package com.microcourse.service;

import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.PageResult;

public interface CourseQueryService {

    PageResult<CourseVO> page(CoursePageQuery query);

    CourseVO getById(Long id);
}
