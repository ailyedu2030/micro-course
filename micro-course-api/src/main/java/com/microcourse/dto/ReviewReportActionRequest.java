package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 举报处理操作请求
 */
public class ReviewReportActionRequest {

    @NotBlank(message = "操作类型不能为空")
    private String action;  // 'DISMISS' | 'REMOVE'

    private String reviewNotes;

    public ReviewReportActionRequest() {}

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
}
