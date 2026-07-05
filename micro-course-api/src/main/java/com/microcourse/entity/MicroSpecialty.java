package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("micro_specialties")
public class MicroSpecialty {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String title;
    private String subtitle;
    private String coverUrl;
    private String description;
    @TableField("offer_department_id")
    private Long offerDepartmentId;
    @TableField("lead_teacher_id")
    private Long leadTeacherId;
    private String targetAudience;
    private String trainingObjective;
    private String admissionRequirement;
    private String completionRule;
    /** P1-I-14: INTEGER 备用列，后续迭代切换为主字段 */
    @TableField(exist = false)
    private Integer completionRuleCode;
    private BigDecimal totalCredits;
    private Integer totalHours;
    private Integer requiredCourseCount;
    private Integer electiveCourseCount;
    private BigDecimal minCredits;
    private Integer maxStudents;
    private Integer studentCount;
    private String semester;
    private Boolean isFeatured;
    private Integer featuredRank;
    private String featuredStatus;
    /** P1-I-14: INTEGER 备用列，后续迭代切换为主字段 */
    @TableField(exist = false)
    private Integer featuredStatusCode;
    private LocalDateTime featuredApplyAt;
    private String featuredApplyReason;
    private Long featuredApprovedBy;
    private LocalDateTime featuredApprovedAt;
    private Boolean isGoldFeatured;
    private Long goldFeaturedBy;
    private LocalDateTime goldFeaturedAt;
    private String status;
    /** P1-I-14: INTEGER 备用列，后续迭代切换为主字段 */
    @TableField(exist = false)
    private Integer statusCode;
    private String rejectReason;
    private String cancelReason;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Long creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;

    public MicroSpecialty() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getOfferDepartmentId() { return offerDepartmentId; }
    public void setOfferDepartmentId(Long offerDepartmentId) { this.offerDepartmentId = offerDepartmentId; }
    public Long getLeadTeacherId() { return leadTeacherId; }
    public void setLeadTeacherId(Long leadTeacherId) { this.leadTeacherId = leadTeacherId; }
    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }
    public String getTrainingObjective() { return trainingObjective; }
    public void setTrainingObjective(String trainingObjective) { this.trainingObjective = trainingObjective; }
    public String getAdmissionRequirement() { return admissionRequirement; }
    public void setAdmissionRequirement(String admissionRequirement) { this.admissionRequirement = admissionRequirement; }
    public String getCompletionRule() { return completionRule; }
    public void setCompletionRule(String completionRule) { this.completionRule = completionRule; }
    public Integer getCompletionRuleCode() { return completionRuleCode; }
    public void setCompletionRuleCode(Integer completionRuleCode) { this.completionRuleCode = completionRuleCode; }
    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }
    public Integer getTotalHours() { return totalHours; }
    public void setTotalHours(Integer totalHours) { this.totalHours = totalHours; }
    public Integer getRequiredCourseCount() { return requiredCourseCount; }
    public void setRequiredCourseCount(Integer requiredCourseCount) { this.requiredCourseCount = requiredCourseCount; }
    public Integer getElectiveCourseCount() { return electiveCourseCount; }
    public void setElectiveCourseCount(Integer electiveCourseCount) { this.electiveCourseCount = electiveCourseCount; }
    public BigDecimal getMinCredits() { return minCredits; }
    public void setMinCredits(BigDecimal minCredits) { this.minCredits = minCredits; }
    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public Boolean getIsFeatured() { return isFeatured; }
    public void setIsFeatured(Boolean isFeatured) { this.isFeatured = isFeatured; }
    public Integer getFeaturedRank() { return featuredRank; }
    public void setFeaturedRank(Integer featuredRank) { this.featuredRank = featuredRank; }
    public String getFeaturedStatus() { return featuredStatus; }
    public void setFeaturedStatus(String featuredStatus) { this.featuredStatus = featuredStatus; }
    public Integer getFeaturedStatusCode() { return featuredStatusCode; }
    public void setFeaturedStatusCode(Integer featuredStatusCode) { this.featuredStatusCode = featuredStatusCode; }
    public LocalDateTime getFeaturedApplyAt() { return featuredApplyAt; }
    public void setFeaturedApplyAt(LocalDateTime featuredApplyAt) { this.featuredApplyAt = featuredApplyAt; }
    public String getFeaturedApplyReason() { return featuredApplyReason; }
    public void setFeaturedApplyReason(String featuredApplyReason) { this.featuredApplyReason = featuredApplyReason; }
    public Long getFeaturedApprovedBy() { return featuredApprovedBy; }
    public void setFeaturedApprovedBy(Long featuredApprovedBy) { this.featuredApprovedBy = featuredApprovedBy; }
    public LocalDateTime getFeaturedApprovedAt() { return featuredApprovedAt; }
    public void setFeaturedApprovedAt(LocalDateTime featuredApprovedAt) { this.featuredApprovedAt = featuredApprovedAt; }
    public Boolean getIsGoldFeatured() { return isGoldFeatured; }
    public void setIsGoldFeatured(Boolean isGoldFeatured) { this.isGoldFeatured = isGoldFeatured; }
    public Long getGoldFeaturedBy() { return goldFeaturedBy; }
    public void setGoldFeaturedBy(Long goldFeaturedBy) { this.goldFeaturedBy = goldFeaturedBy; }
    public LocalDateTime getGoldFeaturedAt() { return goldFeaturedAt; }
    public void setGoldFeaturedAt(LocalDateTime goldFeaturedAt) { this.goldFeaturedAt = goldFeaturedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
