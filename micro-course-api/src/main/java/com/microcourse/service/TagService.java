package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.TagCreateRequest;
import com.microcourse.dto.TagVO;

public interface TagService {

    PageResult<TagVO> page(int page, int size);

    TagVO create(TagCreateRequest request);
}