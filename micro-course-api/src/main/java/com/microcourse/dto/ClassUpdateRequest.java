package com.microcourse.dto;

public class ClassUpdateRequest {

    private String name;
    private Long majorId;
    private String grade;
    private Integer sortOrder;

    public ClassUpdateRequest() {}

    public ClassUpdateRequest(String name, Long majorId, String grade, Integer sortOrder) {
        this.name = name;
        this.majorId = majorId;
        this.grade = grade;
        this.sortOrder = sortOrder;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
