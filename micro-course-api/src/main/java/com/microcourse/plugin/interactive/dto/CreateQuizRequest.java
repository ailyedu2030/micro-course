package com.microcourse.plugin.interactive.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public class CreateQuizRequest {
    @NotNull @Min(1) private Integer slide;
    @NotBlank @Size(max = 2000) private String prompt;
    @NotNull @Size(min = 2, max = 10) private @Valid List<@NotBlank @Size(max = 200) String> options;
    @NotNull @Min(0) @Max(9) private Integer correctIndex;
    @Size(max = 2000) private String explanation;

    public Integer getSlide() { return slide; }
    public void setSlide(Integer slide) { this.slide = slide; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public Integer getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(Integer correctIndex) { this.correctIndex = correctIndex; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
