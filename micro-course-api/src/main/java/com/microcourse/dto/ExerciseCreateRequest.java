package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ExerciseCreateRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private Long chapterId;

    private List<Long> chapterIds;

    @NotBlank(message = "练习标题不能为空")
    private String title;

    private String description;

    private Integer passScore;

    private Integer timeLimit;

    private Integer maxAttempts;

    private String showAnswerWhen;

    private Boolean shuffleQuestions;

    private Boolean shuffleOptions;

    private List<ExerciseQuestionItem> questions;

    private Boolean isExam;

    private Integer totalScore;

    public ExerciseCreateRequest() {}

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public List<Long> getChapterIds() { return chapterIds; }
    public void setChapterIds(List<Long> chapterIds) { this.chapterIds = chapterIds; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getPassScore() { return passScore; }
    public void setPassScore(Integer passScore) { this.passScore = passScore; }
    public Integer getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Integer timeLimit) { this.timeLimit = timeLimit; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getShowAnswerWhen() { return showAnswerWhen; }
    public void setShowAnswerWhen(String showAnswerWhen) { this.showAnswerWhen = showAnswerWhen; }
    public Boolean getShuffleQuestions() { return shuffleQuestions; }
    public void setShuffleQuestions(Boolean shuffleQuestions) { this.shuffleQuestions = shuffleQuestions; }
    public Boolean getShuffleOptions() { return shuffleOptions; }
    public void setShuffleOptions(Boolean shuffleOptions) { this.shuffleOptions = shuffleOptions; }
    public List<ExerciseQuestionItem> getQuestions() { return questions; }
    public void setQuestions(List<ExerciseQuestionItem> questions) { this.questions = questions; }
    public Boolean getIsExam() { return isExam; }
    public void setIsExam(Boolean isExam) { this.isExam = isExam; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }

    public static class ExerciseQuestionItem {
        private Long questionId;
        private Integer score;
        private Integer sortOrder;

        public ExerciseQuestionItem() {}

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}