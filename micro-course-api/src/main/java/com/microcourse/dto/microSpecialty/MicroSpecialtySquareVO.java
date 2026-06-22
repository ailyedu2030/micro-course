package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtySquareVO {
    private List<MicroSpecialtyFeaturedVO> goldFeatured;
    private List<MicroSpecialtyFeaturedVO> featured;
    private List<MicroSpecialtyFeaturedVO> recruiting;
    public MicroSpecialtySquareVO() {}
    public List<MicroSpecialtyFeaturedVO> getGoldFeatured() { return goldFeatured; }
    public void setGoldFeatured(List<MicroSpecialtyFeaturedVO> v) { goldFeatured = v; }
    public List<MicroSpecialtyFeaturedVO> getFeatured() { return featured; }
    public void setFeatured(List<MicroSpecialtyFeaturedVO> v) { featured = v; }
    public List<MicroSpecialtyFeaturedVO> getRecruiting() { return recruiting; }
    public void setRecruiting(List<MicroSpecialtyFeaturedVO> v) { recruiting = v; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MicroSpecialtyFeaturedVO {
        private Long id; private String title; private String coverUrl; private String departmentName;
        private String leadTeacherName; private BigDecimal totalCredits; private Integer courseCount;
        private Integer teacherCount; private Integer studentCount; private String status;
        private Boolean isGoldFeatured; private BigDecimal qualityScore;
        public MicroSpecialtyFeaturedVO() {}
        public Long getId() { return id; } public void setId(Long v) { id = v; }
        public String getTitle() { return title; } public void setTitle(String v) { title = v; }
        public String getCoverUrl() { return coverUrl; } public void setCoverUrl(String v) { coverUrl = v; }
        public String getDepartmentName() { return departmentName; } public void setDepartmentName(String v) { departmentName = v; }
        public String getLeadTeacherName() { return leadTeacherName; } public void setLeadTeacherName(String v) { leadTeacherName = v; }
        public BigDecimal getTotalCredits() { return totalCredits; } public void setTotalCredits(BigDecimal v) { totalCredits = v; }
        public Integer getCourseCount() { return courseCount; } public void setCourseCount(Integer v) { courseCount = v; }
        public Integer getTeacherCount() { return teacherCount; } public void setTeacherCount(Integer v) { teacherCount = v; }
        public Integer getStudentCount() { return studentCount; } public void setStudentCount(Integer v) { studentCount = v; }
        public String getStatus() { return status; } public void setStatus(String v) { status = v; }
        public Boolean getIsGoldFeatured() { return isGoldFeatured; } public void setIsGoldFeatured(Boolean v) { isGoldFeatured = v; }
        public BigDecimal getQualityScore() { return qualityScore; } public void setQualityScore(BigDecimal v) { qualityScore = v; }
    }
}
