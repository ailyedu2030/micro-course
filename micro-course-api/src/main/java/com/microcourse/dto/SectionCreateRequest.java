package com.microcourse.dto;

import jakarta.validation.constraints.*;

public class SectionCreateRequest {
    @NotBlank @Size(max = 200) private String title;
    @NotBlank @Pattern(regexp = "VIDEO|INTERACTIVE|OFFLINE|EXERCISE") private String sectionType;
    @Min(0) private Integer sortOrder = 0;
    @Min(0) private Integer duration = 0;
    private Boolean visible = true;
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
