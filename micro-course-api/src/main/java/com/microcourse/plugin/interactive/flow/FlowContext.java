package com.microcourse.plugin.interactive.flow;

/**
 * FlowContext · 跳转决策的输入上下文 (spec 4.1 / flow/)
 *
 * 包含: 用户进度, quiz 答案, 当前页 id, 等运行时变量.
 */
public class FlowContext {
    private final Long currentPageId;
    private final Long userId;
    private final Double userProgress;       // 0.0 - 1.0
    private final Long lastQuizId;
    private final Boolean lastQuizAnswer;    // true=correct, false=wrong, null=N/A

    public FlowContext(Long currentPageId, Long userId, Double userProgress,
                       Long lastQuizId, Boolean lastQuizAnswer) {
        this.currentPageId = currentPageId;
        this.userId = userId;
        this.userProgress = userProgress;
        this.lastQuizId = lastQuizId;
        this.lastQuizAnswer = lastQuizAnswer;
    }

    public Long getCurrentPageId() { return currentPageId; }
    public Long getUserId() { return userId; }
    public Double getUserProgress() { return userProgress; }
    public Long getLastQuizId() { return lastQuizId; }
    public Boolean getLastQuizAnswer() { return lastQuizAnswer; }
}