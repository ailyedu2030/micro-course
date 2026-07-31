package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.WrongQuestion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WrongQuestionRepository extends BaseMapper<WrongQuestion> {

    /**
     * 幂等插入/更新错题记录：命中 UK (user_id, question_id) 时累加 wrong_count 并刷新 last_wrong_at，
     * 未命中时插入新记录（wrong_count=1）。
     * 用于手动批改判定为错题时同步写入错题本，保证与批改事务一致。
     * 依赖 PostgreSQL ON CONFLICT 特性。
     */
    @Insert("INSERT INTO wrong_questions (user_id, question_id, course_id, wrong_count, last_wrong_at, created_at) "
            + "VALUES (#{userId}, #{questionId}, #{courseId}, 1, NOW(), NOW()) "
            + "ON CONFLICT (user_id, question_id) DO UPDATE SET "
            + "wrong_count = wrong_questions.wrong_count + 1, "
            + "last_wrong_at = NOW(), "
            + "course_id = COALESCE(wrong_questions.course_id, #{courseId})")
    int upsertWrongQuestion(@Param("userId") Long userId,
                            @Param("questionId") Long questionId,
                            @Param("courseId") Long courseId);
}