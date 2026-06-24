package com.microcourse.dto;

import java.time.LocalDateTime;

public class ClassVO {

    private Long id;
    private String name;
    private Long majorId;
    private String majorName;
    private String grade;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public ClassVO() {}

    public ClassVO(Long id, String name, Long majorId, String majorName, String grade,
                   Integer sortOrder, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.majorId = majorId;
        this.majorName = majorName;
        this.grade = grade;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
    public String getMajorName() { return majorName; }
    public void setMajorName(String majorName) { this.majorName = majorName; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
