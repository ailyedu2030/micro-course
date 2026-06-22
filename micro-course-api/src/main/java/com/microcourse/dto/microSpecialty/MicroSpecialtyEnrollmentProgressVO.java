package com.microcourse.dto.microSpecialty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyEnrollmentProgressVO {
    private Long enrollmentId; private Long microSpecialtyId; private String microSpecialtyTitle;
    private BigDecimal progress; private BigDecimal creditsEarned; private BigDecimal totalCredits;
    private Integer coursesCompleted; private Integer coursesRequired;
    private BigDecimal finalScore; private String finalGrade; private String status;
    private Long certificateId; private Boolean canDownloadCert;
    public MicroSpecialtyEnrollmentProgressVO() {}
    public Long getEnrollmentId() { return enrollmentId; } public void setEnrollmentId(Long v) { enrollmentId = v; }
    public Long getMicroSpecialtyId() { return microSpecialtyId; } public void setMicroSpecialtyId(Long v) { microSpecialtyId = v; }
    public String getMicroSpecialtyTitle() { return microSpecialtyTitle; } public void setMicroSpecialtyTitle(String v) { microSpecialtyTitle = v; }
    public BigDecimal getProgress() { return progress; } public void setProgress(BigDecimal v) { progress = v; }
    public BigDecimal getCreditsEarned() { return creditsEarned; } public void setCreditsEarned(BigDecimal v) { creditsEarned = v; }
    public BigDecimal getTotalCredits() { return totalCredits; } public void setTotalCredits(BigDecimal v) { totalCredits = v; }
    public Integer getCoursesCompleted() { return coursesCompleted; } public void setCoursesCompleted(Integer v) { coursesCompleted = v; }
    public Integer getCoursesRequired() { return coursesRequired; } public void setCoursesRequired(Integer v) { coursesRequired = v; }
    public BigDecimal getFinalScore() { return finalScore; } public void setFinalScore(BigDecimal v) { finalScore = v; }
    public String getFinalGrade() { return finalGrade; } public void setFinalGrade(String v) { finalGrade = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Long getCertificateId() { return certificateId; } public void setCertificateId(Long v) { certificateId = v; }
    public Boolean getCanDownloadCert() { return canDownloadCert; } public void setCanDownloadCert(Boolean v) { canDownloadCert = v; }
}
