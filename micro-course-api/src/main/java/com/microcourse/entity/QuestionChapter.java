package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 题目-章节 多对多关联表。
 *
 * <p>复合主键 (question_id, chapter_id)。MyBatis Plus 需要一个字段标注 @TableId 才能生成
 * CRUD SQL。本 entity 历史上只使用 selectList/insert/delete 等基于 (questionId, chapterId)
 * 的查询，因此选取 questionId 作为 @TableId 字段（type=INPUT，由应用代码提供），
 * MyBatis Plus 即不会再产生 'Cannot find table primary key' 警告。
 *
 * @author 总工程师
 */
@TableName("question_chapters")
public class QuestionChapter implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "question_id", type = IdType.INPUT)
    private Long questionId;

    @TableField("chapter_id")
    private Long chapterId;

    public QuestionChapter() {}

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
}
