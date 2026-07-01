package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

@TableName("micro_specialty_proposals")
public class MicroSpecialtyProposal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long proposerId;
    private String title;
    private String description;
    private Long offerDepartmentId;
    private String trainingObjective;
    private String prerequisites;
    private String semester;
    private Integer maxStudents;
    private java.math.BigDecimal credits;
    private String status;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long createdMicroSpecialtyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;

    // ============================================================
    // Phase 15 新增字段（对齐 docs/数据字典.md §12.1）
    // ============================================================

    /** 微专业类型 */
    private String type = "急需紧缺型";

    /** 面向对象（专科/本科/硕士/博士 逗号分隔） */
    private String targetAudience;

    /** 面向学科及专业 */
    private String targetDisciplines;

    /** 总学分 */
    private Integer totalCredits;

    /** 课程门数 */
    private Integer courseCount;

    /** 共建高校 */
    private String coBuildUniversities;

    /** 拟共享高校 */
    private String plannedShareUniversities;

    /** 招生名额 */
    private Integer enrollmentQuota;

    /** 成班人数 */
    private Integer classSize;

    /** 开课时间 */
    private LocalDateTime startDate;

    /** 学制 */
    private String duration;

    /** 是否产教融合 */
    private Boolean isIndustryAcademic;

    /** 产教合作单位 */
    private String industryPartners;

    /** 微专业介绍（富文本） */
    private String introduction;

    /** 社会需求及就业前景分析 */
    private String marketDemandAnalysis;

    /** 微专业简介 */
    private String specialtyOverview;

    /** 课程体系设置情况 */
    private String curriculumDesign;

    /** 建设条件保障 */
    private String constructionGuarantee;

    /** 专业负责人姓名 */
    private String leadName;

    /** 专业负责人职称 */
    private String leadTitle;

    /** 专业负责人职务 */
    private String leadPosition;

    /** 专业负责人联系电话 */
    private String leadPhone;

    /** 主要研究方向 */
    private String leadResearchDirection;

    /** 承担主要任务与主讲课程 */
    private String leadMainTasks;

    /** 联系电话（表头用） */
    private String contactPhone;

    /** 申请时间 */
    private LocalDateTime applyDate;

    /** P0-4 修复：微专业名称（独立于 title 的申报高校名称） */
    private String microSpecialtyName;

    public MicroSpecialtyProposal() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProposerId() { return proposerId; }
    public void setProposerId(Long proposerId) { this.proposerId = proposerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getOfferDepartmentId() { return offerDepartmentId; }
    public void setOfferDepartmentId(Long offerDepartmentId) { this.offerDepartmentId = offerDepartmentId; }
    public String getTrainingObjective() { return trainingObjective; }
    public void setTrainingObjective(String trainingObjective) { this.trainingObjective = trainingObjective; }
    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
    public java.math.BigDecimal getCredits() { return credits; }
    public void setCredits(java.math.BigDecimal credits) { this.credits = credits; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    @JsonProperty("microSpecialtyId")
    public Long getCreatedMicroSpecialtyId() { return createdMicroSpecialtyId; }
    public void setCreatedMicroSpecialtyId(Long createdMicroSpecialtyId) { this.createdMicroSpecialtyId = createdMicroSpecialtyId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    // ============================================================
    // Phase 15 新增字段 getter/setter
    // ============================================================

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }
    public String getTargetDisciplines() { return targetDisciplines; }
    public void setTargetDisciplines(String targetDisciplines) { this.targetDisciplines = targetDisciplines; }
    public Integer getTotalCredits() { return totalCredits; }
    public void setTotalCredits(Integer totalCredits) { this.totalCredits = totalCredits; }
    public Integer getCourseCount() { return courseCount; }
    public void setCourseCount(Integer courseCount) { this.courseCount = courseCount; }
    public String getCoBuildUniversities() { return coBuildUniversities; }
    public void setCoBuildUniversities(String coBuildUniversities) { this.coBuildUniversities = coBuildUniversities; }
    public String getPlannedShareUniversities() { return plannedShareUniversities; }
    public void setPlannedShareUniversities(String plannedShareUniversities) { this.plannedShareUniversities = plannedShareUniversities; }
    public Integer getEnrollmentQuota() { return enrollmentQuota; }
    public void setEnrollmentQuota(Integer enrollmentQuota) { this.enrollmentQuota = enrollmentQuota; }
    public Integer getClassSize() { return classSize; }
    public void setClassSize(Integer classSize) { this.classSize = classSize; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public Boolean getIsIndustryAcademic() { return isIndustryAcademic; }
    public void setIsIndustryAcademic(Boolean isIndustryAcademic) { this.isIndustryAcademic = isIndustryAcademic; }
    public String getIndustryPartners() { return industryPartners; }
    public void setIndustryPartners(String industryPartners) { this.industryPartners = industryPartners; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public String getMarketDemandAnalysis() { return marketDemandAnalysis; }
    public void setMarketDemandAnalysis(String marketDemandAnalysis) { this.marketDemandAnalysis = marketDemandAnalysis; }
    public String getSpecialtyOverview() { return specialtyOverview; }
    public void setSpecialtyOverview(String specialtyOverview) { this.specialtyOverview = specialtyOverview; }
    public String getCurriculumDesign() { return curriculumDesign; }
    public void setCurriculumDesign(String curriculumDesign) { this.curriculumDesign = curriculumDesign; }
    public String getConstructionGuarantee() { return constructionGuarantee; }
    public void setConstructionGuarantee(String constructionGuarantee) { this.constructionGuarantee = constructionGuarantee; }
    public String getLeadName() { return leadName; }
    public void setLeadName(String leadName) { this.leadName = leadName; }
    public String getLeadTitle() { return leadTitle; }
    public void setLeadTitle(String leadTitle) { this.leadTitle = leadTitle; }
    public String getLeadPosition() { return leadPosition; }
    public void setLeadPosition(String leadPosition) { this.leadPosition = leadPosition; }
    public String getLeadPhone() { return leadPhone; }
    public void setLeadPhone(String leadPhone) { this.leadPhone = leadPhone; }
    public String getLeadResearchDirection() { return leadResearchDirection; }
    public void setLeadResearchDirection(String leadResearchDirection) { this.leadResearchDirection = leadResearchDirection; }
    public String getLeadMainTasks() { return leadMainTasks; }
    public void setLeadMainTasks(String leadMainTasks) { this.leadMainTasks = leadMainTasks; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public LocalDateTime getApplyDate() { return applyDate; }
    public void setApplyDate(LocalDateTime applyDate) { this.applyDate = applyDate; }
    public String getMicroSpecialtyName() { return microSpecialtyName; }
    public void setMicroSpecialtyName(String microSpecialtyName) { this.microSpecialtyName = microSpecialtyName; }
}
