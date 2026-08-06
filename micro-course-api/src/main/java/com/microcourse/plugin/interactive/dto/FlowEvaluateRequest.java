package com.microcourse.plugin.interactive.dto;

/**
 * PPT 页间跳转求值请求（P1-1 / R-5，播放器 → 后端 FlowEngine）。
 * 字段与 FlowContext 对齐：currentPageId 必填；userProgress 0.0~1.0
 * （SKIP_IF_KNOWN 用）；lastQuizId/lastQuizAnswer（BRANCH_DEPENDS 用）。
 */
public class FlowEvaluateRequest {

    private Long currentPageId;
    private Double userProgress;
    private Long lastQuizId;
    private Boolean lastQuizAnswer;

    public FlowEvaluateRequest() {}

    public Long getCurrentPageId() { return currentPageId; }
    public void setCurrentPageId(Long currentPageId) { this.currentPageId = currentPageId; }
    public Double getUserProgress() { return userProgress; }
    public void setUserProgress(Double userProgress) { this.userProgress = userProgress; }
    public Long getLastQuizId() { return lastQuizId; }
    public void setLastQuizId(Long lastQuizId) { this.lastQuizId = lastQuizId; }
    public Boolean getLastQuizAnswer() { return lastQuizAnswer; }
    public void setLastQuizAnswer(Boolean lastQuizAnswer) { this.lastQuizAnswer = lastQuizAnswer; }
}
