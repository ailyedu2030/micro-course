package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 教师批改成绩请求 DTO
 * 前端提交 enrollmentId + score + comment，后端反查 courseId / studentId
 */
public class GradeTeacherSubmitRequest {

    @NotNull(message = "选课记录ID不能为空")
    private Long enrollmentId;

    @NotNull(message = "分数不能为空")
    private BigDecimal score;

    private String comment;

    public GradeTeacherSubmitRequest() {}

    public Long getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(Long enrollmentId) { this.enrollmentId = enrollmentId; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
