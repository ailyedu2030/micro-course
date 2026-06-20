package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.bundle.BundleCreateRequest;
import com.microcourse.dto.bundle.BundleVO;

public interface CourseBundleService {

    BundleVO create(BundleCreateRequest request);

    BundleVO getById(Long id);

    PageResult<BundleVO> page(int page, int size);

    void addCourse(Long bundleId, Long courseId, Integer sortOrder, Boolean isRequired);

    void removeCourse(Long bundleId, Long itemId);

    void delete(Long id);
}
