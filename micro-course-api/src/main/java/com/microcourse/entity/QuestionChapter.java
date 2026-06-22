package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

@TableName("question_chapters")
public class QuestionChapter implements Serializable {

    @TableField("question_id")
    private Long questionId;

    @TableField("chapter_id")
    private Long chapterId;

    public QuestionChapter() {}

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
}
