package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 练习-章节 多对多关联表。
 *
 * <p>复合主键 (exercise_id, chapter_id)。MyBatis Plus 需要一个字段标注 @TableId 才能生成
 * CRUD SQL。本 entity 历史上只使用 selectList/insert/delete 等基于 (exerciseId, chapterId)
 * 的查询，因此选取 exerciseId 作为 @TableId 字段（type=INPUT，由应用代码提供），
 * MyBatis Plus 即不会再产生 'Cannot find table primary key' 警告。
 *
 * @author 总工程师
 */
@TableName("exercise_chapters")
public class ExerciseChapter implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "exercise_id", type = IdType.INPUT)
    private Long exerciseId;

    @TableField("chapter_id")
    private Long chapterId;

    public ExerciseChapter() {}

    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
}
