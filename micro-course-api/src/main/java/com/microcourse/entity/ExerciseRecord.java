package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("exercise_records")
public class ExerciseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("exercise_id")
    private Long exerciseId;

    @TableField("user_id")
    private Long userId;

    @TableField("attempt_no")
    private Integer attemptNo;

    private Integer score;

    @TableField("total_score")
    private Integer totalScore;

    private Boolean passed;

    private Integer duration;

    @TableField("answers")
    private String answers;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    public ExerciseRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
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
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}