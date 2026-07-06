package com.microcourse.dto.storage;

import java.util.List;

/**
 * 预览 VO — 纯静态渲染数据，用于 A4 打印预览
 */
public class StorageApplicationPreviewVO {

    private Long id;
    private String title;
    private String microSpecialtyName;
    private String status;
    private String leadName;
    private String contactPhone;
    private String applyDate;
    private String type;
    private String targetAudience;
    private String targetDisciplines;
    private Integer totalCredits;
    private Integer courseCount;
    private String coBuildUniversities;
    private String plannedShareUniversities;
    private Integer enrollmentQuota;
    private Integer classSize;
    private String startDate;
    private String duration;
    private Boolean isIndustryAcademic;
    private String industryPartners;

    private String introduction;
    private String marketDemandAnalysis;
    private String specialtyOverview;
    private String curriculumDesign;
    private String constructionGuarantee;

    private String leadTitle;
    private String leadPosition;
    private String leadResearchDirection;
    private String leadMainTasks;

    private List<ProposalCourseItem> courses;
    private List<ProposalLeadCourseItem> leadCourses;
    private List<ProposalTeamMemberItem> teamMembers;
    private List<ProposalSignatureItem> signatures;
    private List<ProposalSharedUnitItem> sharedUnits;
    private List<ChapterAssignmentItem> chapterAssignments;

    // === 审核信息 ===
    private String reviewComment;

    public StorageApplicationPreviewVO() {}

    // === getter/setter ===

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMicroSpecialtyName() { return microSpecialtyName; }
    public void setMicroSpecialtyName(String microSpecialtyName) { this.microSpecialtyName = microSpecialtyName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLeadName() { return leadName; }
    public void setLeadName(String leadName) { this.leadName = leadName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getApplyDate() { return applyDate; }
    public void setApplyDate(String applyDate) { this.applyDate = applyDate; }
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
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
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

    public String getLeadTitle() { return leadTitle; }
    public void setLeadTitle(String leadTitle) { this.leadTitle = leadTitle; }
    public String getLeadPosition() { return leadPosition; }
    public void setLeadPosition(String leadPosition) { this.leadPosition = leadPosition; }
    public String getLeadResearchDirection() { return leadResearchDirection; }
    public void setLeadResearchDirection(String leadResearchDirection) { this.leadResearchDirection = leadResearchDirection; }

    public String getLeadMainTasks() { return leadMainTasks; }
    public void setLeadMainTasks(String leadMainTasks) { this.leadMainTasks = leadMainTasks; }

    public List<ProposalCourseItem> getCourses() { return courses; }
    public void setCourses(List<ProposalCourseItem> courses) { this.courses = courses; }
    public List<ProposalLeadCourseItem> getLeadCourses() { return leadCourses; }
    public void setLeadCourses(List<ProposalLeadCourseItem> leadCourses) { this.leadCourses = leadCourses; }
    public List<ProposalTeamMemberItem> getTeamMembers() { return teamMembers; }
    public void setTeamMembers(List<ProposalTeamMemberItem> teamMembers) { this.teamMembers = teamMembers; }
    public List<ProposalSignatureItem> getSignatures() { return signatures; }
    public void setSignatures(List<ProposalSignatureItem> signatures) { this.signatures = signatures; }
    public List<ProposalSharedUnitItem> getSharedUnits() { return sharedUnits; }
    public void setSharedUnits(List<ProposalSharedUnitItem> sharedUnits) { this.sharedUnits = sharedUnits; }
    public List<ChapterAssignmentItem> getChapterAssignments() { return chapterAssignments; }
    public void setChapterAssignments(List<ChapterAssignmentItem> chapterAssignments) { this.chapterAssignments = chapterAssignments; }

    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
}
