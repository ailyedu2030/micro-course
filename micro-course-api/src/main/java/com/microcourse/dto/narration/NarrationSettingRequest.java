package com.microcourse.dto.narration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NarrationSettingRequest {

    @NotBlank(message = "演讲人身份不能为空")
    @Size(max = 200, message = "演讲人身份不超过200字")
    private String speakerIdentity;

    @NotBlank(message = "目标受众不能为空")
    @Size(max = 200, message = "目标受众不超过200字")
    private String targetAudience;

    @NotBlank(message = "演讲风格不能为空")
    @Size(max = 200, message = "演讲风格不超过200字")
    private String speakingStyle;

    @Min(value = 1, message = "总讲述时长至少1分钟")
    @Max(value = 120, message = "总讲述时长不超过120分钟")
    private Integer totalDurationMinutes;

    public String getSpeakerIdentity() { return speakerIdentity; }
    public void setSpeakerIdentity(String speakerIdentity) { this.speakerIdentity = speakerIdentity; }

    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }

    public String getSpeakingStyle() { return speakingStyle; }
    public void setSpeakingStyle(String speakingStyle) { this.speakingStyle = speakingStyle; }

    public Integer getTotalDurationMinutes() { return totalDurationMinutes; }
    public void setTotalDurationMinutes(Integer totalDurationMinutes) { this.totalDurationMinutes = totalDurationMinutes; }
}
