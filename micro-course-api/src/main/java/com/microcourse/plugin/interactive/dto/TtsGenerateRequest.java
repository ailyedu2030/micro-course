package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TtsGenerateRequest {

    @NotBlank(message = "voice 不能为空")
    private String voice;

    private String model = "speech-2.8-hd";

    private Double speed = 0.95;

    private Boolean splitByPage = true;

    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }
    public Boolean getSplitByPage() { return splitByPage; }
    public void setSplitByPage(Boolean splitByPage) { this.splitByPage = splitByPage; }
}
