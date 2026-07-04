package com.microcourse.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ExerciseVO {

    private Long id;
    private Long chapterId;
    private String chapterTitle;
    private List<Long> chapterIds;
    private List<String> chapterTitles;
    private Long courseId;
    private String courseTitle;
    private String title;
    private String description;
    private Integer passScore;
    private Integer timeLimit;
    private Integer maxAttempts;
    private String showAnswerWhen;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    private Integer totalScore;
    private Integer questionCount;
    private Boolean isExam;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ExerciseQuestionVO> questions;

    public ExerciseVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public List<Long> getChapterIds() { return chapterIds; }
    public void setChapterIds(List<Long> chapterIds) { this.chapterIds = chapterIds; }
    public List<String> getChapterTitles() { return chapterTitles; }
    public void setChapterTitles(List<String> chapterTitles) { this.chapterTitles = chapterTitles; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
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
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public Boolean getIsExam() { return isExam; }
    public void setIsExam(Boolean isExam) { this.isExam = isExam; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<ExerciseQuestionVO> getQuestions() { return questions; }
    public void setQuestions(List<ExerciseQuestionVO> questions) { this.questions = questions; }

    public static class ExerciseQuestionVO {
        private Long id;
        private Long exerciseId;
        private Long questionId;
        private Integer score;
        private Integer sortOrder;
        // R14 P0-2: 题目完整内容（之前只返回关联字段，不返回题目本身）
        private String questionType;
        private String content;
        private String options;
        private String answer;
        private String explanation;

        public ExerciseQuestionVO() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getExerciseId() { return exerciseId; }
        public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getOptions() { return options; }
        public void setOptions(String options) { this.options = options; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
    }
}