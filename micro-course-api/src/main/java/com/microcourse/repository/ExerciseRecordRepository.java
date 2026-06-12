package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.ExerciseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExerciseRecordRepository extends BaseMapper<ExerciseRecord> {

    /**
     * 平均答题正确率：所有 exercise_records 的 score/totalScore 的平均值
     */
    @Select("SELECT COALESCE(AVG(score * 1.0 / NULLIF(total_score, 0)), 0) " +
            "FROM exercise_records " +
            "WHERE deleted_at IS NULL AND total_score > 0")
    Double selectAvgAccuracyRate();
}