package com.microcourse.plugin.interactive.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public class CreateQuizRequest {
    @NotNull(message = "关联 slide 序号不能为空")
    @Min(value = 1, message = "slide 序号必须 ≥ 1")
    private Integer slide;

    @NotBlank(message = "题目文本不能为空")
    @Size(max = 2000, message = "题目文本不能超过 2000 字")
    private String prompt;

    @NotNull(message = "选项列表不能为空")
    @Size(min = 2, max = 10, message = "选项数量须在 2-10 之间")
    private @Valid List<@NotBlank(message = "选项内容不能为空") @Size(max = 200, message = "选项不能超过 200 字") String> options;

    @NotNull(message = "正确选项索引不能为空")
    @Min(value = 0, message = "正确选项索引必须 ≥ 0")
    @Max(value = 9, message = "正确选项索引必须 ≤ 9")
    private Integer correctIndex;

    @Size(max = 2000, message = "答案解析不能超过 2000 字")
    private String explanation;

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
