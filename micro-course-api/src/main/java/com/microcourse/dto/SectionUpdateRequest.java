package com.microcourse.dto;

import jakarta.validation.constraints.*;

public class SectionUpdateRequest {
    @Size(max = 200) private String title;
    @Pattern(regexp = "VIDEO|INTERACTIVE|OFFLINE|EXERCISE") private String sectionType;
    @Min(0) private Integer sortOrder;
    @Min(0) private Integer duration;
    private Boolean visible;
    @Size(max = 2000) private String description;

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
}
