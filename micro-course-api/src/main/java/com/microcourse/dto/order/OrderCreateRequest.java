package com.microcourse.dto.order;

/**
 * 创建订单请求 DTO
 * 替代原始 Map<String, Long> 请求体
 */
public class OrderCreateRequest {

    private Long courseId;

    private Long bundleId;

    public OrderCreateRequest() {}

    public Long getCourseId() { return courseId; }

    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public Long getBundleId() { return bundleId; }

    public void setBundleId(Long bundleId) { this.bundleId = bundleId; }
}
