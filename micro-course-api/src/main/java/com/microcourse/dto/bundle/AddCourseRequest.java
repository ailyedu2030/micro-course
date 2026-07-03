package com.microcourse.dto.bundle;

import jakarta.validation.constraints.NotNull;

public class AddCourseRequest {
    @NotNull(message = "课程ID不能为空")
    private Long courseId;
    private Integer sortOrder;
    private Boolean isRequired;

    public AddCourseRequest() {}
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }
}
