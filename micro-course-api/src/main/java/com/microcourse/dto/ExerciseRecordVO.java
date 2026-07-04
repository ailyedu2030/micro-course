package com.microcourse.dto;

import java.time.LocalDateTime;

public class ExerciseRecordVO {

    private Long id;
    private Long exerciseId;
    private String exerciseTitle;
    private Long userId;
    private Integer attemptNo;
    private Integer score;
    private Integer totalScore;
    private Boolean passed;
    private Integer duration;
    private String answers;
    private Boolean needsManualGrading;
    private LocalDateTime submittedAt;

    public ExerciseRecordVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
    public String getExerciseTitle() { return exerciseTitle; }
    public void setExerciseTitle(String exerciseTitle) { this.exerciseTitle = exerciseTitle; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getAnswers() { return answers; }
    public void setAnswers(String answers) { this.answers = answers; }
    public Boolean getNeedsManualGrading() { return needsManualGrading; }
    public void setNeedsManualGrading(Boolean needsManualGrading) { this.needsManualGrading = needsManualGrading; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}