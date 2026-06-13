package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SubmitAnswerRequest {

    @NotNull(message = "练习ID不能为空")
    private Long exerciseId;

    private Long userId;

    @NotNull(message = "答案列表不能为空")
    private List<AnswerItem> answers;

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
        private String answer;

        public AnswerItem() {}

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
    }
}