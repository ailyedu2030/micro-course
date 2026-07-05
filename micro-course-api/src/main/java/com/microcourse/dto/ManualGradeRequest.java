package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class ManualGradeRequest {
    @NotNull(message = "分数不能为空")
    private Double score;
    private String comment;

    public ManualGradeRequest() {}
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
