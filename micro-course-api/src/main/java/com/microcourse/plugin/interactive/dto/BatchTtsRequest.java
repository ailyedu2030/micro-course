package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class BatchTtsRequest {

    @NotNull(message = "courseId 不能为空")
    private Long courseId;

    @NotEmpty(message = "sections 不能为空")
    private java.util.List<Long> sections;

    private String voice = "male-qn-qingse";
    private String model = "speech-2.8-hd";
    private Double speed = 0.95;
    private Boolean splitByPage = true;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public java.util.List<Long> getSections() { return sections; }
    public void setSections(java.util.List<Long> sections) { this.sections = sections; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }
    public Boolean getSplitByPage() { return splitByPage; }
    public void setSplitByPage(Boolean splitByPage) { this.splitByPage = splitByPage; }
}
