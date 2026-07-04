package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public class ExamGenerateRequest {

    @NotBlank(message = "考试标题不能为空")
    private String title;

    @NotNull(message = "courseId 不能为空")
    private Long courseId;

    private List<Long> chapterIds;

    @NotNull(message = "请至少选择一种题型")
    private Map<String, Integer> questionCounts;

    private Integer totalScore;

    private Integer timeLimit;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public List<Long> getChapterIds() { return chapterIds; }
    public void setChapterIds(List<Long> chapterIds) { this.chapterIds = chapterIds; }
    public Map<String, Integer> getQuestionCounts() { return questionCounts; }
    public void setQuestionCounts(Map<String, Integer> questionCounts) { this.questionCounts = questionCounts; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
}
