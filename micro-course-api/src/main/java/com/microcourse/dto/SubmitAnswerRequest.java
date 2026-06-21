package com.microcourse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public class SubmitAnswerRequest {

    @NotNull(message = "练习ID不能为空")
    private Long exerciseId;

    private Long userId;

    // ★ Round 9-3 修复：@Valid 级联校验每个 AnswerItem；@Size 限制答案条目数（防超大列表 DoS）
    @NotNull(message = "答案列表不能为空")
    @Size(max = 5000, message = "答案数量超出限制")
    @Valid
    private List<AnswerItem> answers;

    // ★ Round 9-3 修复：时长不能为负数（null 时跳过，合法用户零退化）
    @PositiveOrZero(message = "时长不能为负数")
    private Integer duration;

    private Integer attemptNo;

    public SubmitAnswerRequest() {}

    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public List<AnswerItem> getAnswers() { return answers; }
    public void setAnswers(List<AnswerItem> answers) { this.answers = answers; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }

    public static class AnswerItem {
        private Long questionId;

        // ★ Round 9-3 修复：单条答案长度上限 5000 字符（拦截"数万字答案" → 友好 400 而非 500）
        @Size(max = 5000, message = "答案长度不能超过 5000 字符")
        private String answer;

        public AnswerItem() {}

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
    }
}