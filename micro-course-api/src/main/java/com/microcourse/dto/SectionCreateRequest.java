package com.microcourse.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public class SectionCreateRequest {
    @NotBlank @Size(max = 200) private String title;
    @NotBlank @Pattern(regexp = "VIDEO|INTERACTIVE|OFFLINE|EXERCISE") private String sectionType;
    @Min(0) private Integer sortOrder = 0;
    @Min(0) private Integer duration = 0;
    private Boolean visible = true;
    @Size(max = 2000) private String description;

    // ===== P1 Stage 1: 小节元信息(Trae SKILL.md 模块 3.3 schema)=====
    @Size(max = 20) private String no;
    private List<String> learningObjectives;
    @Size(max = 2000) private String anchorScenarioStep;
    @Size(max = 100) private String coreCompetency;
    @Pattern(regexp = "HTML|PPT|BOTH", message = "coursewareType 必须是 HTML / PPT / BOTH") private String coursewareType;
    @Pattern(regexp = "15-segment|1-merged", message = "audioStrategy 必须是 15-segment / 1-merged") private String audioStrategy;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // ===== P1 Stage 1 getters/setters =====
    public String getNo() { return no; }
    public void setNo(String no) { this.no = no; }
    public List<String> getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(List<String> learningObjectives) { this.learningObjectives = learningObjectives; }
    public String getAnchorScenarioStep() { return anchorScenarioStep; }
    public void setAnchorScenarioStep(String anchorScenarioStep) { this.anchorScenarioStep = anchorScenarioStep; }
    public String getCoreCompetency() { return coreCompetency; }
    public void setCoreCompetency(String coreCompetency) { this.coreCompetency = coreCompetency; }
    public String getCoursewareType() { return coursewareType; }
    public void setCoursewareType(String coursewareType) { this.coursewareType = coursewareType; }
    public String getAudioStrategy() { return audioStrategy; }
    public void setAudioStrategy(String audioStrategy) { this.audioStrategy = audioStrategy; }
}
