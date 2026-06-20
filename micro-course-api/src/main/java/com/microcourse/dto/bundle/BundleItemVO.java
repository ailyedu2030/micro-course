package com.microcourse.dto.bundle;

public class BundleItemVO {
    private Long id;
    private Long bundleId;
    private Long courseId;
    private String courseTitle;
    private String courseType;
    private String teacherName;
    private Integer sortOrder;
    private Boolean isRequired;

    public BundleItemVO() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long bundleId) { this.bundleId = bundleId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }
}
