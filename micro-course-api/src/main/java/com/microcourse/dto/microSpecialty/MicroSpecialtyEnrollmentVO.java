package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyEnrollmentVO {

    private Long id;
    private Long microSpecialtyId;
    private String microSpecialtyTitle;
    private String coverUrl;
    private Long userId;
    private String userName;
    private String source;
    private Long classId;
    private String className;
    private String status;
    private BigDecimal progress;
    private BigDecimal creditsEarned;
    private Integer coursesCompleted;
    private Integer coursesRequired;
    private BigDecimal finalScore;
    private String finalGrade;
    private Long certificateId;
    private Boolean canDownloadCert;
    private String pendingCourses;       // G2: JSON 数组字符串
    private LocalDateTime appliedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private LocalDateTime droppedAt;
    private String dropReason;
    private String failReason;

    public MicroSpecialtyEnrollmentVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMicroSpecialtyId() { return microSpecialtyId; }
    public void setMicroSpecialtyId(Long microSpecialtyId) { this.microSpecialtyId = microSpecialtyId; }

    public String getMicroSpecialtyTitle() { return microSpecialtyTitle; }
    public void setMicroSpecialtyTitle(String microSpecialtyTitle) { this.microSpecialtyTitle = microSpecialtyTitle; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getProgress() { return progress; }
    public void setProgress(BigDecimal progress) { this.progress = progress; }

    public BigDecimal getCreditsEarned() { return creditsEarned; }
    public void setCreditsEarned(BigDecimal creditsEarned) { this.creditsEarned = creditsEarned; }

    public Integer getCoursesCompleted() { return coursesCompleted; }
    public void setCoursesCompleted(Integer coursesCompleted) { this.coursesCompleted = coursesCompleted; }

    public Integer getCoursesRequired() { return coursesRequired; }
    public void setCoursesRequired(Integer coursesRequired) { this.coursesRequired = coursesRequired; }

    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }

    public String getFinalGrade() { return finalGrade; }
    public void setFinalGrade(String finalGrade) { this.finalGrade = finalGrade; }

    public Long getCertificateId() { return certificateId; }
    public void setCertificateId(Long certificateId) { this.certificateId = certificateId; }

    public Boolean getCanDownloadCert() { return canDownloadCert; }
    public void setCanDownloadCert(Boolean canDownloadCert) { this.canDownloadCert = canDownloadCert; }

    public String getPendingCourses() { return pendingCourses; }
    public void setPendingCourses(String pendingCourses) { this.pendingCourses = pendingCourses; }

    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getDroppedAt() { return droppedAt; }
    public void setDroppedAt(LocalDateTime droppedAt) { this.droppedAt = droppedAt; }

    public String getDropReason() { return dropReason; }
    public void setDropReason(String dropReason) { this.dropReason = dropReason; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    /** Transient: compute certificate download URL from certificateId. */
    public String getCertificateUrl() {
        if (certificateId != null) {
            return "/api/certificates/" + certificateId + "/download";
        }
        return null;
    }
}
