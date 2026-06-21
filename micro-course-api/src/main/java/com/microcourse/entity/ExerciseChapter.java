package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

@TableName("exercise_chapters")
public class ExerciseChapter implements Serializable {

    @TableField("exercise_id")
    private Long exerciseId;

    @TableField("chapter_id")
    private Long chapterId;

    public ExerciseChapter() {}

    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
}
