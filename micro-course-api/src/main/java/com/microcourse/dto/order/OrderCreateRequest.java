package com.microcourse.dto.order;

import jakarta.validation.constraints.AssertTrue;

public class OrderCreateRequest {

    private Long courseId;

    private Long bundleId;

    public OrderCreateRequest() {}

    @AssertTrue(message = "courseId 与 bundleId 必须二选一")
    public boolean isExactlyOne() {
        return (courseId != null) ^ (bundleId != null);
    }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long bundleId) { this.bundleId = bundleId; }
}
