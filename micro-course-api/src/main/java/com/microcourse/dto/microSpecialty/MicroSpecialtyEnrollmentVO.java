package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyEnrollmentVO {
    private Long id; private Long microSpecialtyId; private String microSpecialtyTitle; private String coverUrl;
    private Long userId; private String userName; private String source; private Long classId; private String className;
    private String status; private BigDecimal progress; private BigDecimal creditsEarned;
    private Integer coursesCompleted; private Integer coursesRequired; private BigDecimal finalScore;
    private String finalGrade; private Long certificateId; private Boolean canDownloadCert;
    private LocalDateTime appliedAt; private LocalDateTime approvedAt;
    private LocalDateTime completedAt; private LocalDateTime droppedAt; private String dropReason;
    public MicroSpecialtyEnrollmentVO() {}
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getMicroSpecialtyId() { return microSpecialtyId; } public void setMicroSpecialtyId(Long v) { microSpecialtyId = v; }
    public String getMicroSpecialtyTitle() { return microSpecialtyTitle; } public void setMicroSpecialtyTitle(String v) { microSpecialtyTitle = v; }
    public String getCoverUrl() { return coverUrl; } public void setCoverUrl(String v) { coverUrl = v; }
    public Long getUserId() { return userId; } public void setUserId(Long v) { userId = v; }
    public String getUserName() { return userName; } public void setUserName(String v) { userName = v; }
    public String getSource() { return source; } public void setSource(String v) { source = v; }
    public Long getClassId() { return classId; } public void setClassId(Long v) { classId = v; }
    public String getClassName() { return className; } public void setClassName(String v) { className = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public BigDecimal getProgress() { return progress; } public void setProgress(BigDecimal v) { progress = v; }
    public BigDecimal getCreditsEarned() { return creditsEarned; } public void setCreditsEarned(BigDecimal v) { creditsEarned = v; }
    public Integer getCoursesCompleted() { return coursesCompleted; } public void setCoursesCompleted(Integer v) { coursesCompleted = v; }
    public Integer getCoursesRequired() { return coursesRequired; } public void setCoursesRequired(Integer v) { coursesRequired = v; }
    public BigDecimal getFinalScore() { return finalScore; } public void setFinalScore(BigDecimal v) { finalScore = v; }
    public String getFinalGrade() { return finalGrade; } public void setFinalGrade(String v) { finalGrade = v; }
    public Long getCertificateId() { return certificateId; } public void setCertificateId(Long v) { certificateId = v; }
    public Boolean getCanDownloadCert() { return canDownloadCert; } public void setCanDownloadCert(Boolean v) { canDownloadCert = v; }
    public LocalDateTime getAppliedAt() { return appliedAt; } public void setAppliedAt(LocalDateTime v) { appliedAt = v; }
    public LocalDateTime getApprovedAt() { return approvedAt; } public void setApprovedAt(LocalDateTime v) { approvedAt = v; }
    public LocalDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(LocalDateTime v) { completedAt = v; }
    public LocalDateTime getDroppedAt() { return droppedAt; } public void setDroppedAt(LocalDateTime v) { droppedAt = v; }
    public String getDropReason() { return dropReason; } public void setDropReason(String v) { dropReason = v; }
}
