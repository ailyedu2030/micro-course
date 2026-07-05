package com.microcourse.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class AddQuestionsRequest {
    @NotEmpty(message = "题目ID列表不能为空")
    private List<Long> questionIds;

    public AddQuestionsRequest() {}
    public List<Long> getQuestionIds() { return questionIds; }
    public void setQuestionIds(List<Long> questionIds) { this.questionIds = questionIds; }
}
