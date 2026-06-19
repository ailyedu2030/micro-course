package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class GradeCreateRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    private Long exerciseId;

    private BigDecimal score;

    private BigDecimal totalScore;

    private Boolean passed;

    private Integer attemptNo;

    private Integer duration;

    private String comment;

    public GradeCreateRequest() {}

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}