package com.microcourse.dto;

import java.time.LocalDateTime;

public class ClassVO {

    private Long id;
    private String name;
    private Long majorId;
    private String majorName;
    private String grade;
    private Long counselorId;
    private String counselorName;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public ClassVO() {}

    public ClassVO(Long id, String name, Long majorId, String majorName, String grade,
                   Long counselorId, String counselorName, Integer sortOrder, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.majorId = majorId;
        this.majorName = majorName;
        this.grade = grade;
        this.counselorId = counselorId;
        this.counselorName = counselorName;
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
    public Long getCounselorId() { return counselorId; }
    public void setCounselorId(Long counselorId) { this.counselorId = counselorId; }
    public String getCounselorName() { return counselorName; }
    public void setCounselorName(String counselorName) { this.counselorName = counselorName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
