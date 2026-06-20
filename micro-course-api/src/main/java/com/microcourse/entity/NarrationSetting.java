package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("narration_settings")
public class NarrationSetting {

    private Long id;
    private Long courseId;
    private String speakerIdentity;
    private String targetAudience;
    private String speakingStyle;
    private Integer totalDurationMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public String getSpeakerIdentity() { return speakerIdentity; }
    public void setSpeakerIdentity(String speakerIdentity) { this.speakerIdentity = speakerIdentity; }

    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }

    public String getSpeakingStyle() { return speakingStyle; }
    public void setSpeakingStyle(String speakingStyle) { this.speakingStyle = speakingStyle; }

    public Integer getTotalDurationMinutes() { return totalDurationMinutes; }
    public void setTotalDurationMinutes(Integer totalDurationMinutes) { this.totalDurationMinutes = totalDurationMinutes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
