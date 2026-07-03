package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.bundle.BundleCreateRequest;
import com.microcourse.dto.bundle.BundleUpdateRequest;
import com.microcourse.dto.bundle.BundleVO;

public interface CourseBundleService {

    BundleVO create(BundleCreateRequest request);

    BundleVO update(Long id, BundleUpdateRequest request);

    BundleVO getById(Long id);

    PageResult<BundleVO> page(int page, int size);

    void addCourse(Long bundleId, Long courseId, Integer sortOrder, Boolean isRequired);

    void removeCourse(Long bundleId, Long itemId);

    void publish(Long id);

    void unpublish(Long id);

    void delete(Long id);

    boolean isUserEnrolledInBundle(Long userId, Long bundleId);
}
